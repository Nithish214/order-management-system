-- OrderStatus.CANCELLED used to mean "Inventory Service couldn't fulfill this" -- renamed
-- to REJECTED so CANCELLED can mean what anyone calling the API would actually expect it
-- to mean: the customer cancelled it themselves (see OrderController#cancelOrder). Any
-- existing row under the old meaning needs relabeling before the new code ever runs,
-- otherwise it would be silently misread as a customer cancellation it never was.
UPDATE orders SET status = 'REJECTED' WHERE status = 'CANCELLED';

COMMIT;
