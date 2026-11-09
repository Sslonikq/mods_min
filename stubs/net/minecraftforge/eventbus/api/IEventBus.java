package net.minecraftforge.eventbus.api; import java.util.function.Consumer;
public interface IEventBus { <T extends Event> void addListener(Consumer<T> c); <T extends Event> void addListener(EventPriority p, Consumer<T> c); }
