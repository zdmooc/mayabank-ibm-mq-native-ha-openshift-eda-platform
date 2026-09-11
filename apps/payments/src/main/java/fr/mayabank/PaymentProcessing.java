package fr.mayabank;

import javax.jms.*;

final class PaymentProcessing {
    static void run() throws Exception {
        while (!Thread.currentThread().isInterrupted()) {
            try (Connection connection = Mq.connect(false)) {
                connection.start();
                try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                     MessageConsumer consumer = session.createConsumer(session.createQueue("queue:///PAYMENT.REQUEST.Q"));
                     MessageProducer reply = session.createProducer(session.createQueue("queue:///PAYMENT.RESPONSE.Q"));
                     MessageProducer backout = session.createProducer(session.createQueue("queue:///PAYMENT.BACKOUT.Q"))) {
                    System.out.println("READY: authenticated MQ connection; simulation only");
                    while (!Thread.currentThread().isInterrupted()) {
                        Message incoming = consumer.receive(5000);
                        if (incoming == null) continue;
                        Payment payment;
                        try {
                            if (!(incoming instanceof TextMessage text)) throw new IllegalArgumentException("expected text");
                            payment = Payment.decode(text.getText());
                            if (!payment.id().equals(incoming.getJMSCorrelationID()))
                                throw new IllegalArgumentException("correlation mismatch");
                        } catch (IllegalArgumentException invalid) {
                            int count = incoming.propertyExists("JMSXDeliveryCount")
                                    ? incoming.getIntProperty("JMSXDeliveryCount") : 1;
                            if (count >= 3) {
                                TextMessage rejected = session.createTextMessage(
                                        incoming instanceof TextMessage t ? t.getText() : "unsupported type");
                                rejected.setJMSCorrelationID(incoming.getJMSCorrelationID());
                                rejected.setStringProperty("failureReason", invalid.getMessage());
                                rejected.setStringProperty("backoutOrigin", "application");
                                rejected.setIntProperty("originalDeliveryCount", count);
                                backout.send(rejected, DeliveryMode.PERSISTENT, 4, 0);
                                session.commit();
                                System.out.println("BACKOUT correlationId=" + incoming.getJMSCorrelationID() + " deliveryCount=" + count);
                            } else {
                                session.rollback();
                                System.out.println("RETRY correlationId=" + incoming.getJMSCorrelationID() + " deliveryCount=" + count);
                                Thread.sleep(1000);
                            }
                            continue;
                        }
                        TextMessage response = session.createTextMessage("SIMULATED_PROCESSED|" + payment.id());
                        response.setJMSCorrelationID(payment.id());
                        reply.send(response, DeliveryMode.PERSISTENT, 4, 3600000);
                        session.commit();
                        System.out.println("SIMULATED_PROCESSED paymentId=" + payment.id());
                    }
                }
            } catch (JMSException failure) {
                System.err.println("MQ connection/session failure; retry in 5s; code=" + failure.getErrorCode());
                Thread.sleep(5000);
            }
        }
    }
}
