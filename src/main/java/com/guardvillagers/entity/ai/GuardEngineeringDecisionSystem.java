package com.guardvillagers.entity.ai;

import com.guardvillagers.GuardDebugLogger;
import com.guardvillagers.GuardVillagersConfig;
import com.guardvillagers.entity.GuardEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.List;

public final class GuardEngineeringDecisionSystem {

    private GuardEngineeringDecisionSystem() {}

    public enum Reason {
        TARGET_UNREACHABLE_HEIGHT,
        STUCK_NO_PROGRESS,
        NOT_NEEDED,
        UNSAFE_NEARBY_THREAT,
        UNSAFE_LOW_HEALTH
    }

    public record Decision(boolean shouldBuild, boolean shouldAbortIfBuilding, Reason reason) {}

    /**
     * Call when GuardEngineeringGoal.canStart() is checking whether to begin building.
     */
    public static Decision evaluateForStart(GuardEntity guard, ServerWorld world,
            GuardVillagersConfig config) {
        // Don't start building if currently fleeing from TNT
        if (guard.isFleingTnt()) {
            return new Decision(false, false, Reason.NOT_NEEDED);
        }

        LivingEntity target = guard.getTarget();
        if (target == null || !target.isAlive()) {
            return new Decision(false, false, Reason.NOT_NEEDED);
        }

        // Danger check first — never start building if guard is in immediate danger
        Decision danger = evaluateDanger(guard, world, config, target);
        if (danger != null) {
            return danger;
        }

        // High priority: target out of reach due to height
        double heightDiff = target.getY() - guard.getY();
        if (heightDiff > config.engineering.preferLadderAbove) {
            return new Decision(true, false, Reason.TARGET_UNREACHABLE_HEIGHT);
        }

        // No progress toward target = stuck (covers gaps, walls, unreachable terrain)
        // The stuck-tracking in GuardAiController.tickCombatTracking() already detects
        // this reliably without false positives from isIdle() or other heuristics.
        GuardAiController controller = guard.getAiController();
        if (controller != null && controller.getStuckTargetTicks() >
                config.combat.targetStuckGraceTicks) {
            return new Decision(true, false, Reason.STUCK_NO_PROGRESS);
        }

        return new Decision(false, false, Reason.NOT_NEEDED);
    }

    /**
     * Call every tick while GuardEngineeringGoal is actively building, to decide
     * whether to abort because a new threat appeared.
     */
    public static Decision evaluateWhileBuilding(GuardEntity guard, ServerWorld world,
            GuardVillagersConfig config) {
        // Abort building if fleeing from TNT
        if (guard.isFleingTnt()) {
            return new Decision(false, true, Reason.UNSAFE_NEARBY_THREAT);
        }

        LivingEntity target = guard.getTarget();
        Decision danger = evaluateDanger(guard, world, config, target);
        if (danger != null) {
            return danger;
        }
        return new Decision(true, false, Reason.NOT_NEEDED);
    }

    /**
     * Shared danger evaluation: returns a non-null Decision (abort) if the guard
     * is in immediate danger and should not be building right now, or null if safe.
     */
    private static Decision evaluateDanger(GuardEntity guard, ServerWorld world,
            GuardVillagersConfig config, LivingEntity currentTarget) {

        // Low health — too risky to stand still building
        double healthRatio = guard.getHealth() / guard.getMaxHealth();
        if (healthRatio < 0.3D) {
            return new Decision(false, true, Reason.UNSAFE_LOW_HEALTH);
        }

        // Check for hostile entities within melee strike range of the guard,
        // EXCLUDING the current build target (that one's handled by the build itself)
        double dangerRadius = 4.0D; // slightly more than melee reach
        Box dangerBox = guard.getBoundingBox().expand(dangerRadius);
        List<HostileEntity> nearby = world.getEntitiesByClass(
            HostileEntity.class,
            dangerBox,
            entity -> entity.isAlive() && entity != currentTarget
        );

        if (!nearby.isEmpty()) {
            // Multiple threats or one very close threat = unsafe
            boolean veryClose = nearby.stream()
                .anyMatch(e -> guard.squaredDistanceTo(e) <= 4.0D); // 2 blocks
            if (nearby.size() >= 2 || veryClose) {
                return new Decision(false, true, Reason.UNSAFE_NEARBY_THREAT);
            }
        }

        return null; // safe
    }
}