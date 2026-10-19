package com.rmmv.editor.ui.db;

/**
 * The Database window's entity pages, in the order the PC editor shows its tabs.
 *
 * <p>Files live in {@code data/}. Two shapes exist:
 * <ul>
 *   <li><b>array</b> &mdash; a JSON array indexed by record id, where index 0 is
 *       always the reserved {@code null} slot (Actors, Classes, ..., Types).</li>
 *   <li><b>single</b> &mdash; one JSON object for the whole project (System, Terms).</li>
 * </ul>
 *
 * <p>{@link #arrayRecords} marks the one file whose records are themselves JSON
 * arrays ({@code Types.json}: element {@code i} is the list of labels for type
 * {@code i}). Everything else stores JSON objects.
 */
public final class EntityCatalog {

    /** One database page. */
    public static final class Entity {
        /** data/*.json file name. */
        public final String file;
        /** true when the file holds a single JSON object rather than an array. */
        public final boolean single;
        /** true when each array element is itself a JSONArray (Types.json). */
        public final boolean arrayRecords;

        Entity(String file, boolean single, boolean arrayRecords) {
            this.file = file;
            this.single = single;
            this.arrayRecords = arrayRecords;
        }
    }

    /** Tab order of the PC editor's Database window. */
    public static final Entity[] ENTITIES = {
            new Entity("Actors.json",       false, false),
            new Entity("Classes.json",      false, false),
            new Entity("Skills.json",       false, false),
            new Entity("Items.json",        false, false),
            new Entity("Weapons.json",      false, false),
            new Entity("Armors.json",       false, false),
            new Entity("Enemies.json",      false, false),
            new Entity("Troops.json",       false, false),
            new Entity("States.json",       false, false),
            new Entity("Animations.json",   false, false),
            new Entity("Tilesets.json",     false, false),
            new Entity("CommonEvents.json", false, false),
            new Entity("System.json",       true,  false),
            new Entity("Types.json",        false, true),
            new Entity("Terms.json",        true,  false)
    };

    public static final int COUNT = ENTITIES.length;

    private EntityCatalog() { }
}