package com.learn.orderservice.service;

import com.learn.orderservice.entity.Order;
import com.learn.orderservice.entity.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

// Deliberately just CONFIRMED/REJECTED/CANCELLED -- PENDING is the order's very first
// moment (the customer just clicked "place order" themselves, so there's nothing new to
// tell them yet), and FAILED is unused today (see OrderStatus's own comment).
@Service
public class OrderNotificationEmailService {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationEmailService.class);

    private final SesClient sesClient;
    private final String fromEmail;

    public OrderNotificationEmailService(SesClient sesClient, @Value("${notifications.from-email}") String fromEmail) {
        this.sesClient = sesClient;
        this.fromEmail = fromEmail;
    }

    public void sendStatusEmail(Order order) {
        if (fromEmail.isBlank()) {
            // Local dev / any environment with no verified SES identity configured
            // (notifications.from-email defaults to empty -- see application.yml).
            // Notifications are simply off here, not a misconfiguration to warn about on
            // every single order.
            return;
        }
        String subject = subjectFor(order);
        if (subject == null) {
            // PENDING/FAILED -- nothing to email about (see the class comment).
            return;
        }
        String to = order.getUser().getEmail();
        String body = bodyFor(order);

        SendEmailRequest request = SendEmailRequest.builder()
                .source(fromEmail)
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).build())
                        .body(Body.builder().text(Content.builder().data(body).build()).build())
                        .build())
                .build();

        try {
            sesClient.sendEmail(request);
        } catch (SesException e) {
            // Never lets an email failure look like an order failure -- by the time this
            // runs (AFTER_COMMIT, see OrderStatusEmailListener), the order's own status
            // change already committed successfully regardless of what happens here. A
            // stuck SES sandbox (the recipient isn't a verified address yet) is the
            // expected everyday case, not something to alarm on every single order.
            log.warn("Failed to send status email for order {} to {}: {}", order.getId(), to, e.getMessage());
        }
    }

    private String subjectFor(Order order) {
        return switch (order.getStatus()) {
            case CONFIRMED -> "Your order #" + order.getId() + " is confirmed";
            case REJECTED -> "Your order #" + order.getId() + " could not be completed";
            case CANCELLED -> "Your order #" + order.getId() + " has been cancelled";
            case PENDING, FAILED -> null;
        };
    }

    private String bodyFor(Order order) {
        return switch (order.getStatus()) {
            case CONFIRMED -> "Good news -- order #" + order.getId() + " has been confirmed and is being prepared.";
            case REJECTED -> "We're sorry -- order #" + order.getId() + " could not be completed.\n\nReason: "
                    + order.getRejectionReason();
            case CANCELLED -> "Order #" + order.getId() + " has been cancelled as requested.";
            case PENDING, FAILED -> "";
        };
    }
}
