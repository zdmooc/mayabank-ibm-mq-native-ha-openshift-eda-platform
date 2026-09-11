package fr.mayabank;

import java.math.BigDecimal;
import java.util.UUID;
import javax.jms.*;
import java.lang.IllegalStateException;

final class IdempotencyTest {
    static void run() throws Exception {
        String scenario = Mq.env("IDEMPOTENCY_SCENARIO", "baseline");
        Payment payment = new Payment(Mq.env("IDEMPOTENCY_PAYMENT_ID", UUID.randomUUID().toString()), new BigDecimal("17.42"), "EUR");
        if ("baseline".equals(scenario)) {
            exchange(payment, "NEW", false);
            exchange(payment, "DUPLICATE", false);
            exchange(new Payment(payment.id(), new BigDecimal("99.99"), "EUR"), "CONFLICT", false);
        } else if ("after-restart".equals(scenario)) {
            exchange(payment, "DUPLICATE", false);
        } else if ("crash".equals(scenario)) {
            exchange(payment, "DUPLICATE", true);
        } else throw new IllegalArgumentException("unknown scenario");
        PaymentLedger.assertSingleEffect(payment);
        System.out.println("PASS: idempotency scenario=" + scenario + " single unchanged effect; paymentId=" + payment.id());
    }

    static void exchange(Payment payment, String expectedOutcome, boolean crash) throws Exception {
        try (Connection connection = Mq.connect(false)) {
            connection.start();
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                 MessageProducer producer = session.createProducer(session.createQueue("queue:///PAYMENT.REQUEST.Q"));
                 MessageConsumer consumer = session.createConsumer(session.createQueue("queue:///PAYMENT.RESPONSE.Q"),
                         "JMSCorrelationID = '" + payment.id() + "'")) {
                TextMessage request = session.createTextMessage(payment.encode());
                request.setJMSCorrelationID(payment.id());
                if (crash) request.setBooleanProperty("mayabankCrashAfterDb", true);
                producer.send(request, DeliveryMode.PERSISTENT, 4, 0);
                session.commit();
                Message response = consumer.receive(crash ? 180000 : 60000);
                String expectedBody = ("CONFLICT".equals(expectedOutcome) ? "CONFLICT|" : "SIMULATED_PROCESSED|") + payment.id();
                if (!(response instanceof TextMessage text) || !expectedBody.equals(text.getText())
                        || !expectedOutcome.equals(response.getStringProperty("idempotencyOutcome")))
                    throw new IllegalStateException("FAIL: missing or invalid " + expectedOutcome + " response");
                int deliveries = response.getIntProperty("requestDeliveryCount");
                if (crash && deliveries < 2) throw new IllegalStateException("FAIL: MQ redelivery not observed");
                session.commit();
                System.out.println("OBSERVED outcome=" + expectedOutcome + " deliveryCount=" + deliveries + " paymentId=" + payment.id());
            }
        }
    }
}
