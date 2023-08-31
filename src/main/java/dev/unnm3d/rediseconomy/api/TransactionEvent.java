package dev.unnm3d.rediseconomy.api;

import dev.unnm3d.rediseconomy.transaction.Transaction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@AllArgsConstructor
@Getter
@Setter
public class TransactionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private Transaction transaction;

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
