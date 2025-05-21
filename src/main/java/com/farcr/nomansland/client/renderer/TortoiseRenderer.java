package com.farcr.nomansland.client.renderer;

import com.farcr.nomansland.client.NMLModelLayers;
import com.farcr.nomansland.client.model.TortoiseModel;
import com.farcr.nomansland.common.entity.goose.GooseVariant;
import com.farcr.nomansland.common.entity.tortoise.Tortoise;
import com.farcr.nomansland.common.entity.tortoise.TortoiseVariant;
import dev.tazer.mixed_litter.MLRegistries;
import dev.tazer.mixed_litter.variants.MobVariant;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

import static dev.tazer.mixed_litter.VariantUtil.getVariants;

public class TortoiseRenderer extends MobRenderer<Tortoise, TortoiseModel<Tortoise>> {

    public TortoiseRenderer(EntityRendererProvider.Context context) {
        super(context, new TortoiseModel<>(context.bakeLayer(NMLModelLayers.TORTOISE_LAYER)), 0.7F);
    }
    
    @Override
    public ResourceLocation getTextureLocation(Tortoise tortoise) {
        TortoiseVariant variant = null;
        for (Holder<MobVariant> animalVariantHolder : getVariants(tortoise, tortoise.level())) {
            if (animalVariantHolder.value() instanceof TortoiseVariant gooseVariant) {
                variant = gooseVariant;
                break;
            }
        }

        if (variant == null) {
            variant =
                    (TortoiseVariant) tortoise.registryAccess().registryOrThrow(MLRegistries.ANIMAL_VARIANT_KEY).holders()
                            .filter(mobVariantReference -> mobVariantReference.value() instanceof GooseVariant)
                            .findAny().orElseThrow().value();
        }
        ResourceLocation texture = variant.texture;
        return texture.withPath(path -> "textures/" + path + ".png");
    }
}

