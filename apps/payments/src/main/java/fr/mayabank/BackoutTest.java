package fr.mayabank;

import javax.jms.*;
import java.util.Enumeration;
import java.util.UUID;

final class BackoutTest {
    static void run() throws Exception {
        String id = UUID.randomUUID().toString();
        String body = "INVALID-CONTRACT|" + id;
        String selector = "JMSCorrelationID = '" + id + "'";
        try (Connection connection = Mq.connect(false);
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            connection.start();
            // Vérifier les droits de consultation avant de publier le message.
            try (QueueBrowser probe = session.createBrowser(session.createQueue("queue:///PAYMENT.BACKOUT.Q"), selector)) {
                probe.getEnumeration();
            }
            try (MessageProducer producer = session.createProducer(session.createQueue("queue:///PAYMENT.REQUEST.Q"))) {
                TextMessage message = session.createTextMessage(body);
                message.setJMSCorrelationID(id);
                producer.send(message, DeliveryMode.PERSISTENT, 4, 0);
            }
            System.out.println("INVALID_SENT correlationId=" + id);
            long deadline = System.nanoTime() + 60_000_000_000L;
            boolean found = false;
            while (System.nanoTime() < deadline) {
                try (QueueBrowser browser = session.createBrowser(session.createQueue("queue:///PAYMENT.BACKOUT.Q"), selector)) {
                    Enumeration<?> messages = browser.getEnumeration();
                    if (messages.hasMoreElements()) {
                        Message message = (Message) messages.nextElement();
                        if (!(message instanceof TextMessage text) || !body.equals(text.getText())
                                || !"application".equals(message.getStringProperty("backoutOrigin"))
                                || message.getIntProperty("originalDeliveryCount") != 3
                                || !message.propertyExists("failureReason") || messages.hasMoreElements())
                            throw new java.lang.IllegalStateException("FAIL: unexpected backout content/count; correlationId=" + id);
                        found = true;
                        break;
                    }
                }
                Thread.sleep(500);
            }
            if (!found) throw new java.lang.IllegalStateException("FAIL: backout not observed within 60s; correlationId=" + id);
            assertAbsent(session, "PAYMENT.REQUEST.Q", selector);
            assertAbsent(session, "PAYMENT.RESPONSE.Q", selector);
            System.out.println("PASS: backout observed without consuming; deliveryCount=3; no pending request or success response; correlationId=" + id);
        }
    }

    private static void assertAbsent(Session session, String queue, String selector) throws JMSException {
        try (QueueBrowser browser = session.createBrowser(session.createQueue("queue:///" + queue), selector)) {
            if (browser.getEnumeration().hasMoreElements())
                throw new java.lang.IllegalStateException("FAIL: unexpected message in " + queue);
        }
    }
}
