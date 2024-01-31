package net.minestom.server.adventure.audience;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.entity.Player;
import net.minestom.server.network.ConnectionManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

/**
 * A provider of iterable audiences.
 */
class IterableAudienceProvider implements AudienceProvider<Iterable<? extends Audience>> {
    private final ConnectionManager connectionManager;
    private final List<ConsoleSender> console;
    private final AudienceRegistry registry = new AudienceRegistry(new ConcurrentHashMap<>(), CopyOnWriteArrayList::new);

    protected IterableAudienceProvider(ConnectionManager connectionManager, CommandManager commandManager) {
        this.connectionManager = connectionManager;
        this.console = List.of(commandManager.getConsoleSender());
    }

    @Override
    public @NotNull Iterable<? extends Audience> all() {
        List<Audience> all = new ArrayList<>();
        this.players().forEach(all::add);
        this.console().forEach(all::add);
        this.customs().forEach(all::add);
        return all;
    }

    @Override
    public @NotNull Iterable<? extends Audience> players() {
        return connectionManager.getOnlinePlayers();
    }

    @Override
    public @NotNull Iterable<? extends Audience> players(@NotNull Predicate<Player> filter) {
        return connectionManager.getOnlinePlayers().stream().filter(filter).toList();
    }

    @Override
    public @NotNull Iterable<? extends Audience> console() {
        return this.console;
    }

    @Override
    public @NotNull Iterable<? extends Audience> server() {
        List<Audience> all = new ArrayList<>();
        this.players().forEach(all::add);
        this.console().forEach(all::add);
        return all;
    }

    @Override
    public @NotNull Iterable<? extends Audience> customs() {
        return this.registry.all();
    }

    @Override
    public @NotNull Iterable<? extends Audience> custom(@NotNull Key key) {
        return this.registry.of(key);
    }

    @Override
    public @NotNull Iterable<? extends Audience> custom(@NotNull Key key, Predicate<Audience> filter) {
        return StreamSupport.stream(this.registry.of(key).spliterator(), false).filter(filter).toList();
    }

    @Override
    public @NotNull Iterable<? extends Audience> customs(@NotNull Predicate<Audience> filter) {
        return this.registry.of(filter);
    }

    @Override
    public @NotNull Iterable<? extends Audience> all(@NotNull Predicate<Audience> filter) {
        return StreamSupport.stream(this.all().spliterator(), false).filter(filter).toList();
    }

    @Override
    public @NotNull AudienceRegistry registry() {
        return this.registry;
    }
}
