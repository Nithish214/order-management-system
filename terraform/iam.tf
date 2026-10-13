# The one role the app's EC2 instance runs as -- CloudWatch metrics/logs, plus three
# narrow inline grants for the exact things the app itself needs to touch directly (SES
# for order-status emails, two S3 prefixes for admin-uploaded product media). No
# wildcard resources anywhere -- each inline policy names the exact ARN it needs, the
# same least-privilege shape the project's own README already describes.
resource "aws_iam_role" "ec2_app" {
  name = "ec2-app-cloudwatch-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "ec2.amazonaws.com" }
        Action    = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "ec2_app" {
  name = "ec2-app-cloudwatch-profile"
  role = aws_iam_role.ec2_app.name
}

resource "aws_iam_role_policy_attachment" "cloudwatch_agent" {
  role       = aws_iam_role.ec2_app.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

# Lets the app's order-status-email feature send mail as this one verified SES identity
# -- not a verified domain, not SES's broader send permissions, exactly this address.
resource "aws_iam_role_policy" "order_notification_email" {
  name = "OrderNotificationEmailPolicy"
  role = aws_iam_role.ec2_app.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "AllowOrderNotificationEmails"
        Effect   = "Allow"
        Action   = ["ses:SendEmail", "ses:SendRawEmail"]
        Resource = "arn:aws:ses:${var.aws_region}:${var.aws_account_id}:identity/ramesh97908@gmail.com"
      }
    ]
  })
}

# Scoped to the product-images/ prefix only -- see s3.tf once the frontend bucket itself
# is under Terraform, this Resource ARN should reference that bucket instead of being
# typed out here a second time.
resource "aws_iam_role_policy" "product_image_upload" {
  name = "ProductImageUploadPolicy"
  role = aws_iam_role.ec2_app.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "AllowProductImageUploads"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:DeleteObject"]
        Resource = "arn:aws:s3:::order-management-frontend-${var.aws_account_id}/product-images/*"
      }
    ]
  })
}

resource "aws_iam_role_policy" "product_video_upload" {
  name = "ProductVideoUploadPolicy"
  role = aws_iam_role.ec2_app.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "AllowProductVideoUploads"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:DeleteObject"]
        Resource = "arn:aws:s3:::order-management-frontend-${var.aws_account_id}/product-videos/*"
      }
    ]
  })
}
