package com.birblett.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Extremely simple mixin plugin to disable compat mixins if target mod is not loaded.
 */
public class MixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            AnnotationNode annotationNode = Annotations.getVisible(MixinService.getService().getBytecodeProvider()
                    .getClassNode(mixinClassName), RequiresMod.class);
            //noinspection unchecked
            List<String> args = (List<String>) annotationNode.values.get(1);
            if (!args.isEmpty() &&  !FabricLoader.getInstance().isModLoaded(args.getFirst())) {
                return false;
            }
            else if (args.size() > 1) {
                Optional<ModContainer> c = FabricLoader.getInstance().getModContainer(args.getFirst());
                if (!(c.isPresent() && c.get().getMetadata().getVersion().toString().equals(args.get(1)))) {
                    return false;
                }
            }
        }
        catch (ClassNotFoundException | IOException e) {
            return false;
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

}
