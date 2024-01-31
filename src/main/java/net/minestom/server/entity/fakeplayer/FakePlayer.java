package net.minestom.server.entity.fakeplayer;

import com.extollit.gaming.ai.path.HydrazinePathFinder;
import net.minestom.server.ServerFacade;
import net.minestom.server.ServerSettings;
import net.minestom.server.adventure.bossbar.BossBarManager;
import net.minestom.server.command.CommandManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.pathfinding.NavigableEntity;
import net.minestom.server.entity.pathfinding.Navigator;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.exception.ExceptionHandler;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.listener.manager.PacketListenerManager;
import net.minestom.server.network.ConnectionManager;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.player.FakePlayerConnection;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.socket.Server;
import net.minestom.server.recipe.RecipeManager;
import net.minestom.server.scoreboard.TeamManager;
import net.minestom.server.thread.ThreadDispatcher;
import net.minestom.server.timer.SchedulerManager;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A fake player will behave exactly the same way as would do a {@link Player} backed by a socket connection
 * (events, velocity, gravity, player list, etc...) with the exception that you need to control it server-side
 * using a {@link FakePlayerController} (see {@link #getController()}).
 * <p>
 * You can create one using {link #initPlayer(ServerProcess, UUID, String, Consumer)}. Be aware that this really behave exactly like a player
 * and this is a feature not a bug, you will need to check at some place if the player is a fake one or not (instanceof) if you want to change it.
 */
public class FakePlayer extends Player implements NavigableEntity {

    private final FakePlayerOption option;
    private final FakePlayerController fakePlayerController;

    private final Navigator navigator = new Navigator(this);

    private EventListener<PlayerSpawnEvent> spawnListener;
    private final SchedulerManager scheduleManager;

    public FakePlayer(ServerFacade serverFacade, @NotNull UUID uuid, @NotNull String username, @NotNull FakePlayerOption option, @Nullable Consumer<FakePlayer> spawnCallback) {
        this(
                serverFacade.getServerSettings(),
                serverFacade.getGlobalEventHandler(),
                serverFacade.getChunkDispatcher(),
                serverFacade.getExceptionHandler(),
                serverFacade.getConnectionManager(),
                serverFacade.getTeamManager(),
                serverFacade.getRecipeManager(),
                serverFacade.getCommandManager(),
                serverFacade.getBossBarManager(),
                serverFacade.getSchedulerManager(),
                serverFacade.getPacketListenerManager(),
                serverFacade.getBlockManager(),
                serverFacade.getServer(),
                uuid, username, option, spawnCallback
        );
    }

    /**
     * Initializes a new {@link FakePlayer} with the given {@code uuid}, {@code username} and {@code option}'s.
     *
     * @param uuid     The unique identifier for the fake player.
     * @param username The username for the fake player.
     * @param option   Any option for the fake player.
     */
    public FakePlayer(ServerSettings serverSettings,
                      EventNode<Event> globalEventHandler,
                      ThreadDispatcher<Chunk> dispatcher,
                      ExceptionHandler exceptionHandler,
                      ConnectionManager connectionManager,
                      TeamManager teamManager,
                      RecipeManager recipeManager,
                      CommandManager commandManager,
                      BossBarManager bossBarManager,
                      SchedulerManager schedulerManager,
                      PacketListenerManager packetListenerManager,
                      BlockManager blockManager,
                      Server server,
                      @NotNull UUID uuid,
                      @NotNull String username,
                      @NotNull FakePlayerOption option,
                      @Nullable Consumer<FakePlayer> spawnCallback) {
        super(serverSettings, globalEventHandler, dispatcher, exceptionHandler, connectionManager, teamManager, recipeManager, commandManager, bossBarManager, schedulerManager, packetListenerManager, blockManager, uuid, username, new FakePlayerConnection(server, connectionManager));
        this.scheduleManager = schedulerManager;

        this.option = option;

        this.fakePlayerController = new FakePlayerController(this);

        if (spawnCallback != null) {
            spawnListener = EventListener.builder(PlayerSpawnEvent.class)
                    .expireWhen(ignored -> this.isRemoved())
                    .handler(event -> {
                        if (event.getPlayer().equals(this))
                            if (event.isFirstSpawn()) {
                                spawnCallback.accept(this);
                                globalEventHandler.removeListener(spawnListener);
                            }
                    }).build();
            globalEventHandler.addListener(spawnListener);
        }

        playerConnection.setConnectionState(ConnectionState.LOGIN);
        connectionManager.transitionLoginToConfig(this).thenRun(() -> {
            // Need to immediately reply with login acknowledged for the player to enter config.
            packetListenerManager.processClientPacket(new ClientLoginAcknowledgedPacket(), getPlayerConnection());
        });
    }

//    /**
//     * Initializes a new {@link FakePlayer}.
//     *
//     * @param uuid          the FakePlayer uuid
//     * @param username      the FakePlayer username
//     * @param spawnCallback the optional callback called when the fake player first spawn
//     */
//    public static void initPlayer(@NotNull ServerProcess serverProcess, @NotNull UUID uuid, @NotNull String username,
//                                  @NotNull FakePlayerOption option, @Nullable Consumer<FakePlayer> spawnCallback) {
//        new FakePlayer(serverProcess, uuid, username, option, spawnCallback);
//    }

//    /**
//     * Initializes a new {@link FakePlayer} without adding it in cache.
//     * <p>
//     * If you want the fake player to be obtainable with the {@link ConnectionManagerImpl}
//     * you need to specify it in a {@link FakePlayerOption} and use {@link #initPlayer(ServerProcess, UUID, String, FakePlayerOption, Consumer)}.
//     *
//     * @param uuid          the FakePlayer uuid
//     * @param username      the FakePlayer username
//     * @param spawnCallback the optional callback called when the fake player first spawn
//     */
//    public static void initPlayer(@NotNull ServerProcess serverProcess, @NotNull UUID uuid, @NotNull String username, @Nullable Consumer<FakePlayer> spawnCallback) {
//        initPlayer(serverProcess, uuid, username, new FakePlayerOption(), spawnCallback);
//    }

    /**
     * Gets the fake player option container.
     *
     * @return the fake player option
     */
    @NotNull
    public FakePlayerOption getOption() {
        return option;
    }

    /**
     * Retrieves the controller for the fake player.
     *
     * @return The fake player's controller.
     */
    @NotNull
    public FakePlayerController getController() {
        return fakePlayerController;
    }

    @Override
    public void update(long time) {
        super.update(time);
        // Path finding
        this.navigator.tick();
    }

    @Override
    public CompletableFuture<Void> setInstance(@NotNull Instance instance, @NotNull Pos spawnPosition) {
        this.navigator.setPathFinder(new HydrazinePathFinder(navigator.getPathingEntity(), instance.getInstanceSpace()));

        return super.setInstance(instance, spawnPosition);
    }

    @Override
    public void updateNewViewer(@NotNull Player player) {
        player.sendPacket(getAddPlayerToList());
        handleTabList(player.getPlayerConnection());
        super.updateNewViewer(player);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void showPlayer(@NotNull PlayerConnection connection) {
        super.showPlayer(connection);
        handleTabList(connection);
    }

    @NotNull
    @Override
    public Navigator getNavigator() {
        return navigator;
    }

    private void handleTabList(PlayerConnection connection) {
        if (!option.isInTabList()) {
            // Remove from tab-list
            scheduleManager.buildTask(() -> connection.sendPacket(getRemovePlayerToList())).delay(20, TimeUnit.getServerTick(serverSettings)).schedule();
        }
    }
}
