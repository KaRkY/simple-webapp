package simple.simple_webapp.email;

public enum EmailTemplateType {
    TEXT, HTML;

    public static EmailTemplateType fromString(String value) {
        return valueOf(value.toUpperCase());
    }

    public String toDbValue() {
        return name().toLowerCase();
    }
}
