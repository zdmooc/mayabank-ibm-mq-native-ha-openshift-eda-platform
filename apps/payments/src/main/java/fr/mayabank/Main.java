package fr.mayabank;

import com.ibm.mq.MQException;
import javax.jms.JMSException;

public final class Main {
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "process" : args[0];
        switch (mode) {
            case "send" -> PaymentOrder.run();
            case "process" -> PaymentProcessing.run();
            case "auth-negative" -> {
                try (var connection = Mq.connect(true)) {
                    throw new IllegalStateException("FAIL: wrong password accepted");
                } catch (JMSException e) {
                    Exception linked = e.getLinkedException();
                    if (!(linked instanceof MQException mq) || mq.getReason() != 2035) throw e;
                    System.out.println("PASS: wrong password rejected (MQRC 2035)");
                }
            }
            default -> throw new IllegalArgumentException("unknown mode");
        }
    }
}
