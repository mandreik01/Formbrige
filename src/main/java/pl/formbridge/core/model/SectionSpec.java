package pl.formbridge.core.model;

import java.util.ArrayList;
import java.util.List;

public final class SectionSpec {

    public String title;
    public final String techName;
    public final boolean repeating;
    public String description = "";
    public Condition showWhen;
    public final List<FieldSpec> fields = new ArrayList<>();
    public Integer minItemRows;

    public SectionSpec(String title, String techName, boolean repeating) {
        this.title = title;
        this.techName = techName;
        this.repeating = repeating;
    }

    public static SectionSpec of(String title, String techName) {
        return new SectionSpec(title, techName, false);
    }

    public static SectionSpec repeating(String title, String techName) {
        return new SectionSpec(title, techName, true);
    }

    public SectionSpec add(FieldSpec f) {
        fields.add(f);
        return this;
    }

    public SectionSpec description(String d) {
        this.description = d == null ? "" : d;
        return this;
    }

    public SectionSpec when(Condition c) {
        this.showWhen = c;
        return this;
    }
}
