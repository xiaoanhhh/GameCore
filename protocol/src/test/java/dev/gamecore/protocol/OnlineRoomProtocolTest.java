package dev.gamecore.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 追加控制字段但不复用已发布字段；客户端只声明选择意图，世界和槽位仍由服务器决定。 */
class OnlineRoomProtocolTest {
    @Test void controlsAndCapabilitiesRoundTripWithoutMovingExistingFields() throws Exception{
        assertEquals(6,GameProtocol.Join.getDescriptor().findFieldByName("platform_ticket").getNumber());
        assertEquals(7,GameProtocol.Join.getDescriptor().findFieldByName("interactive").getNumber());
        assertEquals(6,GameProtocol.Input.getDescriptor().findFieldByName("command").getNumber());
        var control=GameProtocol.Envelope.newBuilder().setProtocolVersion(2).setRequestId(4).setControl(GameProtocol.RoomControl.newBuilder().setRoomId("fight-1").setMatchId("match").setSequence(1).setRevision(2).setAction(GameProtocol.RoomAction.SELECT).addChoices(GameProtocol.Option.newBuilder().setKey("one").setValue("NAIWA"))).build();
        assertEquals(control,GameProtocol.Envelope.parseFrom(control.toByteArray()));
        assertFalse(GameProtocol.Welcome.getDefaultInstance().getSessionControls());
    }
    @Test void controlCannotUploadWorldOrAssertPlayerSlot(){
        var descriptor=GameProtocol.RoomControl.getDescriptor();for(String field:new String[]{"player_slot","health","state","position","geometry","seed"}){assertNull(descriptor.findFieldByName(field));}
        assertNotNull(GameProtocol.Input.getDescriptor().findFieldByName("input_epoch"));
    }
}
