package com.elderlexicon.mod.wizard.client;

import com.elderlexicon.mod.ElderLexicon;
import com.elderlexicon.mod.university.Discipline;
import com.elderlexicon.mod.wizard.Wizard;
import net.minecraft.resources.ResourceLocation;

/**
 * How a Wizard dresses (docs/mago-design.md, 5i): by where it lives, and at a university by its rank, so one tells them
 * apart without reading their names. The robes are drawn by tools/caster_skins.py.
 */
public enum WizardLook {
    /** On its own: a weathered cloak, the hood up. */
    WILDS("wilds", Hat.NONE),
    /** In a village: a homely blue robe, a soft cap. */
    VILLAGE("village", Hat.NONE),
    /** In its tower: indigo sown with stars, the pointed hat. */
    TOWER("tower", Hat.POINTED),
    /** The one who listens: a plain grey gown. */
    AUDITOR("auditor", Hat.NONE),
    /** The one who writes: ink blue, a satchel, a beret with its quill. */
    SCRIBA("scriba", Hat.BERET),
    /** The one who reads: black, a blue stole, its hood on its back. */
    LECTOR("lector", Hat.NONE),
    /** The master: black and velvet, bars on its sleeves, a stole and a tassel of its chair's colour, a square cap. */
    MAGISTER("magister", Hat.SQUARE),
    /** The one who presides: scarlet and ermine, a chain of gold, a tall velvet cap. */
    RECTOR("rector", Hat.VELVET),
    /** A university's Emissary on the roads: a traveller's cloak with the house's seal, a satchel, a wide hat. */
    EMISSARY("emissary", Hat.BRIM);

    /** What it wears on its head, beyond what its robe's texture draws. */
    public enum Hat {
        NONE, POINTED, SQUARE, VELVET, BERET, BRIM
    }

    private final ResourceLocation texture;
    private final Hat hat;

    WizardLook(String name, Hat hat) {
        this.texture = new ResourceLocation(ElderLexicon.MODID, "textures/entity/wizard/" + name + ".png");
        this.hat = hat;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public Hat hat() {
        return hat;
    }

    public static WizardLook of(Wizard wizard) {
        return wizard.rank().map(rank -> switch (rank) {
            case AUDITOR -> AUDITOR;
            case SCRIBA -> SCRIBA;
            case LECTOR -> LECTOR;
            case MAGISTER -> MAGISTER;
            case RECTOR -> RECTOR;
        }).orElseGet(() -> switch (wizard.dwelling()) {
            case WILDS -> WILDS;
            case VILLAGE -> VILLAGE;
            case TOWER -> TOWER;
            case UNIVERSITY -> AUDITOR;
            case EMISSARY -> EMISSARY;
        });
    }

    /**
     * The colour of a chair (docs/mago-design.md, 4.4), on its master's stole and tassel: the four families of the
     * language, the arts, the Archive, the Rector's gold.
     */
    public static int chairColor(Discipline chair) {
        return switch (chair) {
            case FONTES -> 0xD8572A;
            case FUNCOES -> 0x3A6FD8;
            case FILTROS -> 0x3E9E4A;
            case FATOS -> 0xE0C040;
            case GALDRARIA -> 0x9AA4B0;
            case VITA -> 0xD84A7A;
            case NAHVA -> 0x8A4AD0;
            case ARQUIVO -> 0x8A5A32;
            case REITOR -> 0xDCB846;
        };
    }
}
