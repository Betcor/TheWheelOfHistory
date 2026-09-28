package kolo.engine.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kolo.engine.error.Checks;
import kolo.engine.error.ErrorCode;
import kolo.engine.error.ErrorDetails;
import kolo.engine.error.ValidationException;
import kolo.engine.state.GrammaticalCase;
import kolo.engine.state.LocalizedName;
import kolo.engine.state.NounPhrase;

/**
 * Шаблон тексту фрагмента передісторії (GD §4.7), розібраний і перевірений при завантаженні контенту.
 *
 * <p>Змінні:
 *
 * <ul>
 *   <li>{@code {year}} — рік події;
 *   <li>{@code {country}} — коротка назва держави в називному, {@code {country.<відмінок>}} — в іншому відмінку
 *       ({@code {country.instrumental}} → «Велором»);
 *   <li>{@code {country_full}}, {@code {country_full.<відмінок>}} — повна назва;
 *   <li>{@code {neighbor}}, {@code {neighbor_full}} з тими самими відмінками — сусідня держава.
 * </ul>
 *
 * <p>Рід назви шаблон не знає, тож дієслово не узгоджується з назвою: підметом у тексті стоїть «країна», а назва — у
 * непрямих відмінках або без дієслова («Війна з {neighbor.instrumental} закінчилася поразкою»).
 */
public final class BackstoryText {

    private final String template;
    private final List<Part> parts;

    /** @throws ValidationException з {@link ErrorCode#INVALID_TEMPLATE}, якщо шаблон не розбирається */
    public BackstoryText(String field, String template) {
        Checks.notBlank(field, template);
        if (!template.strip().equals(template)) {
            throw invalid(field, template);
        }
        this.template = template;
        this.parts = parse(field, template);
    }

    public String template() {
        return template;
    }

    public boolean usesYear() {
        return parts.stream().anyMatch(part -> part instanceof Year);
    }

    public boolean usesNeighbor() {
        return parts.stream().anyMatch(part -> part instanceof Name name && name.subject() == Subject.NEIGHBOR);
    }

    /**
     * Текст для конкретної держави.
     *
     * @param neighbor сусід; потрібен, лише якщо шаблон його згадує
     * @throws ValidationException з {@link ErrorCode#BLANK_VALUE}, якщо шаблон згадує сусіда, а його не передано
     */
    public String render(LocalizedName country, Optional<LocalizedName> neighbor, int year) {
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(neighbor, "neighbor");
        if (usesNeighbor() && neighbor.isEmpty()) {
            throw new ValidationException(ErrorCode.BLANK_VALUE, ErrorDetails.of("field", "neighbor"));
        }
        StringBuilder text = new StringBuilder();
        for (Part part : parts) {
            switch (part) {
                case Literal(String literal) -> text.append(literal);
                case Year() -> text.append(year);
                case Name(Subject subject, boolean full, GrammaticalCase grammaticalCase) -> {
                    LocalizedName name = subject == Subject.COUNTRY ? country : neighbor.orElseThrow();
                    NounPhrase phrase = full ? name.fullName() : name.shortName();
                    text.append(phrase.form(grammaticalCase));
                }
            }
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BackstoryText text && template.equals(text.template);
    }

    @Override
    public int hashCode() {
        return template.hashCode();
    }

    @Override
    public String toString() {
        return template;
    }

    private static List<Part> parse(String field, String template) {
        List<Part> parts = new ArrayList<>();
        int at = 0;
        while (at < template.length()) {
            int open = template.indexOf('{', at);
            int stray = template.indexOf('}', at);
            if (stray >= 0 && (open < 0 || stray < open)) {
                throw invalid(field, template);
            }
            if (open < 0) {
                parts.add(new Literal(template.substring(at)));
                break;
            }
            if (open > at) {
                parts.add(new Literal(template.substring(at, open)));
            }
            int close = template.indexOf('}', open);
            if (close < 0) {
                throw invalid(field, template);
            }
            parts.add(variable(field, template.substring(open + 1, close)));
            at = close + 1;
        }
        return List.copyOf(parts);
    }

    private static Part variable(String field, String variable) {
        if (variable.equals("year")) {
            return new Year();
        }
        int dot = variable.indexOf('.');
        String subject = dot < 0 ? variable : variable.substring(0, dot);
        GrammaticalCase grammaticalCase = GrammaticalCase.NOMINATIVE;
        if (dot >= 0) {
            grammaticalCase =
                    caseOf(variable.substring(dot + 1)).orElseThrow(() -> invalid(field, "{" + variable + "}"));
        }
        return switch (subject) {
            case "country" -> new Name(Subject.COUNTRY, false, grammaticalCase);
            case "country_full" -> new Name(Subject.COUNTRY, true, grammaticalCase);
            case "neighbor" -> new Name(Subject.NEIGHBOR, false, grammaticalCase);
            case "neighbor_full" -> new Name(Subject.NEIGHBOR, true, grammaticalCase);
            default -> throw invalid(field, "{" + variable + "}");
        };
    }

    private static Optional<GrammaticalCase> caseOf(String key) {
        for (GrammaticalCase grammaticalCase : GrammaticalCase.values()) {
            if (grammaticalCase.key().equals(key)) {
                return Optional.of(grammaticalCase);
            }
        }
        return Optional.empty();
    }

    static ValidationException invalid(String field, String value) {
        return new ValidationException(ErrorCode.INVALID_TEMPLATE, ErrorDetails.of("field", field, "value", value));
    }

    private enum Subject {
        COUNTRY,
        NEIGHBOR
    }

    private sealed interface Part permits Literal, Year, Name {}

    private record Literal(String text) implements Part {}

    private record Year() implements Part {}

    private record Name(Subject subject, boolean full, GrammaticalCase grammaticalCase) implements Part {}
}
