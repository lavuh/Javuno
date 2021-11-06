package solar.rpg.javuno.server.controllers;

import org.jetbrains.annotations.NotNull;
import solar.rpg.javuno.models.packets.AbstractJavunoInOutPlayerPacket;
import solar.rpg.javuno.models.packets.IJavunoDistributedPacket;
import solar.rpg.javuno.models.packets.IJavunoTimeLimitedPacket;
import solar.rpg.javuno.models.packets.JavunoBadPacketException;
import solar.rpg.javuno.models.packets.in.JavunoPacketInOutChatMessage;
import solar.rpg.javuno.models.packets.in.JavunoPacketInOutPlayerReadyChanged;
import solar.rpg.javuno.models.packets.in.JavunoPacketInServerConnect;
import solar.rpg.javuno.mvc.JMVC;
import solar.rpg.javuno.server.controllers.HostController.JavunoServerHost;
import solar.rpg.javuno.server.models.JavunoPacketTimeoutException;
import solar.rpg.javuno.server.models.ServerGameLobbyModel;
import solar.rpg.javuno.server.views.MainFrame;
import solar.rpg.jserver.packet.JServerPacket;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * {@code JavunoServerPacketValidatorHandler} is a delegate class of {@link ServerGameController} and has access to
 * its {@code JMVC} object to call the appropriate view & controller methods.
 * Using this it is able to validate that any received {@code in} packet contains valid data, from a valid source.
 * If an error is found, {@link JavunoBadPacketException} is thrown; this can be used to relay information back
 * to the origin address. Some more serious violations may result in a disconnection.
 *
 * @author jskinner
 * @since 1.0.0
 */
public final class JavunoServerPacketValidatorHandler {

    @NotNull
    private final Logger logger;
    @NotNull
    private final JMVC<MainFrame, ServerGameController> mvc;
    /**
     * Records the last time a specific type of packet was received.
     */
    @NotNull
    private final Map<InetSocketAddress, Map<Class<? extends JServerPacket>, Long>> lastReceivedTimeMap;

    /**
     * Constructs a new {@code JavunoServerPacketValidatorHandler} instance.
     *
     * @param mvc    MVC object that belongs to the {@code ServerGameController}.
     * @param logger Logger object.
     */
    public JavunoServerPacketValidatorHandler(
            @NotNull JMVC<MainFrame, ServerGameController> mvc,
            @NotNull Logger logger) {
        this.mvc = mvc;
        this.logger = logger;
        lastReceivedTimeMap = Collections.synchronizedMap(new HashMap<>());
    }

    public void onPlayerDisconnect(@NotNull InetSocketAddress originAddress) {
        lastReceivedTimeMap.remove(originAddress);
    }

    /**
     * Public facing method so any inbound packet can be handled by the server appropriately.
     * This method delegates the validation and handling of each type of packet to various functions.
     *
     * @param packet The inbound packet (from a client) to handle.
     * @throws JavunoBadPacketException There was a validation error or a problem handling the packet.
     */
    public void handlePacket(@NotNull JServerPacket packet) throws JavunoBadPacketException {
        if (packet instanceof IJavunoTimeLimitedPacket) validateTimeLimitedPacket(packet);
        if (packet instanceof AbstractJavunoInOutPlayerPacket playerPacket) handlePlayerPacket(playerPacket);

        if (packet instanceof JavunoPacketInOutChatMessage chatPacket) validateChatPacket(chatPacket);
        else if (packet instanceof JavunoPacketInServerConnect connectPacket) handleConnectPacket(connectPacket);
        else if (packet instanceof JavunoPacketInOutPlayerReadyChanged readyChangedPacket)
            handlePlayerReadyChanged(readyChangedPacket);
        else throw new JavunoBadPacketException(
                    String.format("Unexpected packet type %s", packet.getClass().getSimpleName()),
                    true);

        if (packet instanceof IJavunoDistributedPacket distributedPacket) {
            JavunoServerHost serverHost = getHostController().getServerHost();
            if (distributedPacket.distributeToSender()) serverHost.writePacketAll(packet);
            else serverHost.writePacketAllExcept(packet, packet.getOriginAddress());
        }
    }

    /**
     * Validates a {@link IJavunoTimeLimitedPacket} to check that additional instances have not been sent within
     * the specified time limit.
     *
     * @param packet The time limited packet to validate.
     * @throws IllegalArgumentException     Provided packet was not a {@link IJavunoTimeLimitedPacket}.
     * @throws JavunoPacketTimeoutException Packet of same type was sent before the time limit expired.
     */
    private void validateTimeLimitedPacket(@NotNull JServerPacket packet) {
        if (!(packet instanceof IJavunoTimeLimitedPacket timeLimitedPacket))
            throw new IllegalArgumentException("Packet is not time limited");

        Map<Class<? extends JServerPacket>, Long> lastReceivedTimePacketClassesMap;
        if (lastReceivedTimeMap.containsKey(packet.getOriginAddress()))
            lastReceivedTimePacketClassesMap = lastReceivedTimeMap.get(packet.getOriginAddress());
        else {
            lastReceivedTimePacketClassesMap = Collections.synchronizedMap(new HashMap<>());
            lastReceivedTimeMap.put(packet.getOriginAddress(), lastReceivedTimePacketClassesMap);
        }
        Class<? extends JServerPacket> packetClass = packet.getClass();

        if (lastReceivedTimePacketClassesMap.containsKey(packetClass)) {
            long lastReceived = System.currentTimeMillis() - lastReceivedTimePacketClassesMap.get(packetClass);
            if (lastReceived < timeLimitedPacket.getLimitDuration())
                throw new JavunoPacketTimeoutException(
                        String.format(
                                "Packet of type %s was received less than %dms ago (∆%dms)",
                                packetClass.getSimpleName(),
                                timeLimitedPacket.getLimitDuration(),
                                lastReceived),
                        true);
        }

        lastReceivedTimePacketClassesMap.put(packetClass, System.currentTimeMillis());
    }

    /**
     * Handles all instances of {@link AbstractJavunoInOutPlayerPacket} and sets the player name to the name of the
     * player who is associated with the origin address of the packet.
     *
     * @param playerPacket The player packet to handle.
     * @throws JavunoBadPacketException Player name in packet was already set.
     */
    private void handlePlayerPacket(@NotNull AbstractJavunoInOutPlayerPacket playerPacket) throws JavunoBadPacketException {
        try {
            String playerName = getModel().getPlayerName(playerPacket.getOriginAddress());
            playerPacket.setPlayerName(playerName);
        } catch (IllegalArgumentException e) {
            throw new JavunoBadPacketException(
                    String.format("Unable to process player packet: %s", e.getMessage()),
                    true);
        }
    }

    /**
     * Handles an incoming connection packet after a connection was established with a client.
     * It will first check if the server password is correct, and then if the wanted name is available.
     *
     * @param connectPacket The connection packet to handle.
     */
    private void handleConnectPacket(@NotNull JavunoPacketInServerConnect connectPacket) {
        mvc.getController().handleConnectingPlayer(connectPacket.getOriginAddress(),
                                                   connectPacket.getWantedPlayerName(),
                                                   connectPacket.getServerPassword());
    }

    private void handlePlayerReadyChanged(
            @NotNull JavunoPacketInOutPlayerReadyChanged readyChangedPacket) throws JavunoBadPacketException {
        try {
            mvc.getController().onPlayerReadyChanged(readyChangedPacket.getOriginAddress(),
                                                     readyChangedPacket.isReady());
            mvc.getController().tryGameStart();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new JavunoBadPacketException(
                    String.format("Unable to change player ready status: %s", e.getMessage()),
                    false);
        }
    }

    private void validateChatPacket(@NotNull JavunoPacketInOutChatMessage chatPacket) throws JavunoBadPacketException {
        try {
            InetSocketAddress originAddress = getModel().getOriginAddress(chatPacket.getSenderName());
            if (!originAddress.equals(chatPacket.getOriginAddress()))
                throw new IllegalArgumentException(
                        String.format("This packet was not expected from %s", chatPacket.getOriginAddress()));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new JavunoBadPacketException(
                    String.format("Unable to process chat packet: %s", e.getMessage()),
                    false);
        }
    }

    /* MVC */

    @NotNull
    private ServerGameLobbyModel getModel() {
        return mvc.getController().getGameLobbyModel();
    }

    @NotNull
    private HostController getHostController() {
        return mvc.getView().getMVC().getController().getHostController();
    }
}