package dev.unnm3d.rediseconomy.api;

import dev.unnm3d.rediseconomy.transaction.Transaction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
@Getter
public class TransactionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private Transaction transaction;

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
