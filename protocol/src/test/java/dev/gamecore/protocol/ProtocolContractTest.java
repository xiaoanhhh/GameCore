package dev.gamecore.protocol;

import com.google.protobuf.ByteString;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 固定线格式/oneof/输入权限边界；编译与运行均使用Gradle生成类，不维护手写协议类。 */
class ProtocolContractTest {
    @Test void predictiveMetadataAddsFieldsWithoutRenumberingExistingInputsOrAcknowledgement() throws Exception {
        assertEquals(2, TankProtocol.InputAck.getDescriptor().findFieldByName("sequence").getNumber());
        assertEquals(3, TankProtocol.InputAck.getDescriptor().findFieldByName("received_sequence").getNumber());
        assertEquals(7, TankProtocol.ServerHello.getDescriptor().findFieldByName("prediction_supported").getNumber());
        var ack = TankProtocol.InputAck.newBuilder().setSequence(5).setReceivedSequence(7).setAppliedTick(30).setExpiresTick(45)
                .setActiveInput(TankProtocol.PlayerInput.newBuilder().setSequence(5).setTargetTick(30).setMove(TankProtocol.MoveDirection.FORWARD)).build();
        assertEquals(ack, TankProtocol.InputAck.parseFrom(ack.toByteArray()));
    }
    @Test void goldenHelloHasStableVersionRequestAndFieldNumbers() throws Exception {
        var value = TankProtocol.Envelope.newBuilder().setProtocolVersion(1).setRequestId(9)
                .setClientHello(TankProtocol.ClientHello.newBuilder().setClientName("cli")).build();
        assertEquals("0801100952050a03636c69", HexFormat.of().formatHex(value.toByteArray()));
        assertEquals(value, TankProtocol.Envelope.parseFrom(value.toByteArray()));
    }
    @Test void allTenPayloadsRoundTripAndOneofReplacesPreviousPayload() throws Exception {
        var builders = List.of(
                TankProtocol.Envelope.newBuilder().setClientHello(TankProtocol.ClientHello.getDefaultInstance()),
                TankProtocol.Envelope.newBuilder().setServerHello(TankProtocol.ServerHello.getDefaultInstance()),
                TankProtocol.Envelope.newBuilder().setJoinRoomRequest(TankProtocol.JoinRoomRequest.getDefaultInstance()),
                TankProtocol.Envelope.newBuilder().setJoinRoomResponse(TankProtocol.JoinRoomResponse.getDefaultInstance()),
                TankProtocol.Envelope.newBuilder().setPlayerInput(TankProtocol.PlayerInput.getDefaultInstance()),
                TankProtocol.Envelope.newBuilder().setWorldSnapshot(TankProtocol.WorldSnapshot.getDefaultInstance()),
                TankProtocol.Envelope.newBuilder().setGameEvent(TankProtocol.GameEvent.getDefaultInstance()),
                TankProtocol.Envelope.newBuilder().setMatchResult(TankProtocol.MatchResult.getDefaultInstance()),
                TankProtocol.Envelope.newBuilder().setError(TankProtocol.ErrorMessage.getDefaultInstance()),
                TankProtocol.Envelope.newBuilder().setHeartbeat(TankProtocol.Heartbeat.getDefaultInstance()));
        for (var builder : builders) {
            var value = builder.setProtocolVersion(1).setRequestId(123).build();
            assertEquals(value, TankProtocol.Envelope.parseFrom(value.toByteArray())); assertEquals(123, value.getRequestId());
        }
        assertFalse(builders.getFirst().setError(TankProtocol.ErrorMessage.getDefaultInstance()).build().hasClientHello());
    }
    @Test void inputCannotDeclareIdentityPositionHealthOrSpeedAndUnknownEnumsRemainDetectable() throws Exception {
        var descriptor = TankProtocol.PlayerInput.getDescriptor();
        for (String name : List.of("player_id", "player_slot", "x", "y", "health", "speed")) { assertNull(descriptor.findFieldByName(name)); }
        var input = TankProtocol.PlayerInput.newBuilder().setMoveValue(999).setAimDegrees(Double.NaN).build();
        var decoded = TankProtocol.PlayerInput.parseFrom(ByteString.copyFrom(input.toByteArray()));
        assertEquals(TankProtocol.MoveDirection.UNRECOGNIZED, decoded.getMove()); assertTrue(Double.isNaN(decoded.getAimDegrees()));
        // Protobuf本身能承载NaN，必须由服务器语义校验拒绝，而不是假定解析器会拦截。
    }
}
