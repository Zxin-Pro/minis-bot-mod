package com.minisbot.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 机器人背包系统
 */
public class BotInventory {
    private final ItemStack[] slots;
    private final int size;

    public BotInventory(int size) {
        this.size = size;
        this.slots = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            slots[i] = ItemStack.EMPTY;
        }
    }

    public boolean addItem(ItemStack stack) {
        for (int i = 0; i < size; i++) {
            if (slots[i].isEmpty()) {
                slots[i] = stack.copy();
                return true;
            }
            if (ItemStack.isSameItemSameComponents(slots[i], stack)) {
                int space = slots[i].getMaxStackSize() - slots[i].getCount();
                if (space > 0) {
                    int toAdd = Math.min(space, stack.getCount());
                    slots[i].grow(toAdd);
                    stack.shrink(toAdd);
                    if (stack.isEmpty()) return true;
                }
            }
        }
        return stack.isEmpty();
    }

    public boolean hasItemCategory(Predicate<ItemStack> predicate) {
        for (ItemStack slot : slots) {
            if (!slot.isEmpty() && predicate.test(slot)) return true;
        }
        return false;
    }

    public int getItemCount() {
        int count = 0;
        for (ItemStack slot : slots) {
            if (!slot.isEmpty()) count++;
        }
        return count;
    }

    public String getFormattedList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (!slots[i].isEmpty()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(slots[i].getHoverName().getString())
                        .append(" x").append(slots[i].getCount());
            }
        }
        return sb.isEmpty() ? "空" : sb.toString();
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Size", size);
        for (int i = 0; i < size; i++) {
            if (!slots[i].isEmpty()) {
                tag.put("Slot" + i, slots[i].save((net.minecraft.core.RegistryAccess) null));
            }
        }
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        if (!tag.contains("Size")) return;
        int s = tag.getInt("Size");
        for (int i = 0; i < Math.min(s, size); i++) {
            if (tag.contains("Slot" + i)) {
                slots[i] = ItemStack.parse(
                        (net.minecraft.core.RegistryAccess) null,
                        tag.getCompound("Slot" + i)
                ).orElse(ItemStack.EMPTY);
            }
        }
    }
}
