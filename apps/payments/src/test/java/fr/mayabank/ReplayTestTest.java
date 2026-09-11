package fr.mayabank;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;

class ReplayTestTest {
    @Test void extractsUtf8Payload() throws Exception {
        byte[] id = new byte[24];
        var m = DlqTest.envelope(ReplayTest.TARGET, id, "épreuve", 2085);
        assertArrayEquals("épreuve".getBytes(StandardCharsets.UTF_8), ReplayTest.payload(m, id, "épreuve"));
    }
    @Test void rejectsBusinessDestination() throws Exception {
        byte[] id = new byte[24];
        var m = DlqTest.envelope("PAYMENT.REQUEST.Q", id, "probe", 2085);
        assertThrows(IllegalStateException.class, () -> ReplayTest.payload(m, id, "probe"));
    }
    @Test void rejectsAlteredReplayPayload() throws Exception {
        byte[] id = new byte[24];
        var m = ReplayTest.plain(id, "altered".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> ReplayTest.checkPlain(m, id, "original"));
    }
}
