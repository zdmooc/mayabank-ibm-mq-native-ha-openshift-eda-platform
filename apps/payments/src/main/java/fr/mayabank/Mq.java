package fr.mayabank;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import javax.jms.Connection;
import java.nio.file.Files;
import java.nio.file.Path;

final class Mq {
    static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }

    static Connection connect(boolean wrongPassword) throws Exception {
        MQConnectionFactory factory = new MQConnectionFactory();
        String connectionNameList = env("MQ_CONNECTION_NAME_LIST", "").strip();
        if (connectionNameList.isEmpty()) {
            factory.setHostName(env("MQ_HOST", "mq"));
            factory.setPort(Integer.parseInt(env("MQ_PORT", "1414")));
        } else {
            factory.setConnectionNameList(connectionNameList);
        }
        factory.setQueueManager(env("MQ_QUEUE_MANAGER", "QM.MAYABANK"));
        factory.setChannel(env("MQ_CHANNEL", "DEV.APP.SVRCONN"));
        factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
        factory.setClientReconnectOptions(WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR);
        factory.setClientReconnectTimeout(Integer.parseInt(env("MQ_RECONNECT_TIMEOUT_SECONDS", "300")));

        String authMode = env("MQ_AUTH_MODE", "password").strip().toLowerCase();
        if ("mtls".equals(authMode)) {
            if (wrongPassword) throw new IllegalArgumentException("password-negative test is not valid in mTLS mode");
            configureTls(factory);
            return factory.createConnection();
        }
        if (!"password".equals(authMode)) throw new IllegalArgumentException("unsupported MQ_AUTH_MODE=" + authMode);

        factory.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
        Path passwordFile = Path.of(env("MQ_PASSWORD_FILE", "/etc/mayabank/mq/mqAppPassword"));
        if (!Files.isReadable(passwordFile))
            throw new java.io.IOException("MQ credential file missing or unreadable: " + passwordFile);
        String password = Files.readString(passwordFile).strip();
        if (password.isEmpty()) throw new java.io.IOException("MQ credential file is empty");
        if (wrongPassword) password = "deliberately-invalid-" + java.util.UUID.randomUUID();
        return factory.createConnection(env("MQ_USER", "app"), password);
    }

    private static void configureTls(MQConnectionFactory factory) throws Exception {
        Path keyStore = Path.of(env("MQ_TLS_KEYSTORE", "/etc/mayabank/tls/client.p12"));
        Path trustStore = Path.of(env("MQ_TLS_TRUSTSTORE", "/etc/mayabank/tls/trust.p12"));
        Path keyStorePasswordFile = Path.of(env("MQ_TLS_KEYSTORE_PASSWORD_FILE", "/etc/mayabank/tls/keystore-password"));
        Path trustStorePasswordFile = Path.of(env("MQ_TLS_TRUSTSTORE_PASSWORD_FILE", "/etc/mayabank/tls/truststore-password"));
        requireReadable(keyStore, "client keystore");
        requireReadable(trustStore, "client truststore");
        requireReadable(keyStorePasswordFile, "keystore password file");
        requireReadable(trustStorePasswordFile, "truststore password file");
        String keyPass = Files.readString(keyStorePasswordFile).strip();
        String trustPass = Files.readString(trustStorePasswordFile).strip();
        if (keyPass.isEmpty() || trustPass.isEmpty()) throw new java.io.IOException("TLS store password is empty");

        System.setProperty("javax.net.ssl.keyStore", keyStore.toString());
        System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");
        System.setProperty("javax.net.ssl.keyStorePassword", keyPass);
        System.setProperty("javax.net.ssl.trustStore", trustStore.toString());
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
        System.setProperty("javax.net.ssl.trustStorePassword", trustPass);
        factory.setSSLCipherSuite(env("MQ_SSL_CIPHER_SUITE", "TLS_AES_256_GCM_SHA384"));
    }

    private static void requireReadable(Path path, String label) throws java.io.IOException {
        if (!Files.isReadable(path)) throw new java.io.IOException(label + " missing or unreadable: " + path);
    }
}
