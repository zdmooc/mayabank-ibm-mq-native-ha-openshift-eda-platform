package fr.mayabank;

import java.math.BigDecimal;
import java.util.UUID;
import javax.jms.*;

final class PaymentOrder {
    static void run() throws Exception {
        Payment payment = new Payment(UUID.randomUUID().toString(), new BigDecimal("12.34"), "EUR");
        try (Connection connection = Mq.connect(false)) {
            connection.start();
            try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
                Queue request = session.createQueue("queue:///PAYMENT.REQUEST.Q");
                Queue response = session.createQueue("queue:///PAYMENT.RESPONSE.Q");
                try (MessageProducer producer = session.createProducer(request);
                     MessageConsumer consumer = session.createConsumer(response,
                             "JMSCorrelationID = '" + payment.id() + "'")) {
                    TextMessage message = session.createTextMessage(payment.encode());
                    message.setJMSCorrelationID(payment.id());
                    producer.send(message, DeliveryMode.PERSISTENT, 4, 0);
                    session.commit();
                    System.out.println("ACCEPTED paymentId=" + payment.id());
                    Message reply = consumer.receive(60000);
                    String expected = "SIMULATED_PROCESSED|" + payment.id();
                    if (!(reply instanceof TextMessage text) || !expected.equals(text.getText())) {
                        session.rollback();
                        throw new java.lang.IllegalStateException("FAIL: missing or invalid response; paymentId=" + payment.id());
                    }
                    session.commit();
                    System.out.println("PASS: authenticated JMS request/reply paymentId=" + payment.id());
                }
            }
        }
    }
}
