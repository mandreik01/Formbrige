package pl.formbridge.core.model;

import java.util.ArrayList;
import java.util.List;

public final class FieldSpec {

    public enum Type {
        TEXT_FIELD, TEXTAREA, NUMBER, DATEPICKER, SELECT, RADIO, CHECKBOX,
        FILE_UPLOADER, TEXT_BLOCK, INFO_SECTION, CONSENT_SECTION
    }

    public record Consent(String descriptionHtml, boolean required) {
    }

    public Type type;
    public String title = "";
    public String techName;
    public String description = "";
    public final List<String> options = new ArrayList<>();
    public final List<String> validation = new ArrayList<>();
    public boolean readOnly;
    public String dictionaryName;
    public Boolean multiLevelTree;
    public Boolean searchEnabled;
    public Object defaultValue;
    public String defaultValueType;
    public Condition showWhen;
    public String tooltipMessage = "";
    public final List<Consent> consents = new ArrayList<>();
    public String sourceText = "";

    public FieldSpec(Type type, String title, String techName) {
        this.type = type;
        this.title = title == null ? "" : title;
        this.techName = techName;
    }

    public static FieldSpec of(Type type, String title, String techName) {
        return new FieldSpec(type, title, techName);
    }

    public FieldSpec required() {
        if (!validation.contains("REQUIRED")) {
            validation.add(0, "REQUIRED");
        }
        return this;
    }

    public FieldSpec validate(String... validators) {
        for (String v : validators) {
            if (!validation.contains(v)) {
                validation.add(v);
            }
        }
        return this;
    }

    public FieldSpec options(List<String> opts) {
        options.addAll(opts);
        return this;
    }

    public FieldSpec description(String d) {
        this.description = d == null ? "" : d;
        return this;
    }

    public FieldSpec readOnlyValue(String value) {
        this.readOnly = true;
        this.defaultValue = value;
        return this;
    }

    public FieldSpec external(String sourceAttribute) {
        this.readOnly = true;
        this.defaultValue = sourceAttribute;
        this.defaultValueType = "EXTERNAL_SYSTEM";
        return this;
    }

    public FieldSpec prefilled(String sourceAttribute) {
        this.defaultValue = sourceAttribute;
        this.defaultValueType = "EXTERNAL_SYSTEM";
        return this;
    }

    public FieldSpec dictionary(String name, boolean multiLevel) {
        this.dictionaryName = name;
        this.multiLevelTree = multiLevel;
        this.searchEnabled = true;
        return this;
    }

    public FieldSpec when(Condition c) {
        this.showWhen = c;
        return this;
    }

    public boolean isInput() {
        return switch (type) {
            case TEXT_BLOCK, INFO_SECTION -> false;
            default -> true;
        };
    }
}
