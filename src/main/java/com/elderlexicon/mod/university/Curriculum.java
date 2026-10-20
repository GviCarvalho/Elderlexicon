package com.elderlexicon.mod.university;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the university teaches (docs/universidade-cursos-design.md), read from {@value #RESOURCE}: the chairs of the
 * first year, every student's, and each course with its chairs. A chair is a module of several lessons, each a part of
 * the mod's tutorial, closed by an examination; a chair several courses list is the same chair in all of them.
 */
public final class Curriculum {

    public static final String RESOURCE = "/data/elderlexicon/lexicon/cursos.json";
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** A lesson of a chair: its id within the chair, its title, the runes it leads the student to understand. */
    public record Lesson(String id, String title, List<String> runes) {
    }

    /** A question of a chair's examination: the prompt, the right answer, then the wrong ones. */
    public record Question(String prompt, String right, List<String> wrong) {
    }

    /** A chair: its title, what it is about, the runes it teaches, its lessons, its examination. */
    public record Chair(String id, String title, String about, List<String> runes, List<Lesson> lessons,
                        List<Question> exam) {
    }

    /** A course: its title, its specialist, its chairs in order. */
    public record CourseInfo(Course course, String title, String specialist, List<String> chairs) {
    }

    private final List<String> firstYear;
    private final Map<Course, CourseInfo> courses;
    private final Map<String, Chair> chairs;

    private Curriculum(List<String> firstYear, Map<Course, CourseInfo> courses, Map<String, Chair> chairs) {
        this.firstYear = firstYear;
        this.courses = courses;
        this.chairs = chairs;
    }

    private static volatile Curriculum read;

    /** What the house teaches, read once. */
    public static Curriculum get() {
        Curriculum found = read;
        if (found == null) {
            synchronized (Curriculum.class) {
                found = read;
                if (found == null) {
                    found = load();
                    read = found;
                }
            }
        }
        return found;
    }

    /** The chairs of the first year, in order. */
    public List<String> firstYear() {
        return firstYear;
    }

    public Optional<CourseInfo> course(Course course) {
        return Optional.ofNullable(courses.get(course));
    }

    public Optional<Chair> chair(String id) {
        return Optional.ofNullable(chairs.get(id));
    }

    public Map<String, Chair> chairs() {
        return chairs;
    }

    /** The courses that list {@code chair}: more than one, it is shared. */
    public List<Course> coursesOf(String chair) {
        List<Course> found = new ArrayList<>();
        courses.forEach((course, info) -> {
            if (info.chairs().contains(chair)) {
                found.add(course);
            }
        });
        return found;
    }

    /** The title of the chair {@code id}, or the id itself if the house does not know it. */
    public String title(String id) {
        return chair(id).map(Chair::title).orElse(id);
    }

    /** The title of {@code course}. */
    public String title(Course course) {
        return course(course).map(CourseInfo::title).orElse(course.id());
    }

    // ------------------------------------------------------------------ reading

    private static Curriculum load() {
        try (InputStream stream = Curriculum.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                LOGGER.warn("No curriculum at {}", RESOURCE);
                return empty();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (java.io.IOException | RuntimeException unreadable) {
            LOGGER.error("The curriculum at {} could not be read", RESOURCE, unreadable);
            return empty();
        }
    }

    private static Curriculum empty() {
        return new Curriculum(List.of(), Map.of(), Map.of());
    }

    static Curriculum parse(JsonObject root) {
        Map<String, Chair> chairs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("cadeiras").entrySet()) {
            JsonObject chair = entry.getValue().getAsJsonObject();
            List<Lesson> lessons = new ArrayList<>();
            for (JsonElement element : array(chair, "aulas")) {
                JsonObject lesson = element.getAsJsonObject();
                lessons.add(new Lesson(lesson.get("id").getAsString(), lesson.get("titulo").getAsString(),
                        strings(array(lesson, "runas"))));
            }
            List<Question> exam = new ArrayList<>();
            for (JsonElement element : array(chair, "exame")) {
                List<String> answers = strings(element.getAsJsonObject().getAsJsonArray("respostas"));
                if (answers.size() >= 2) {
                    exam.add(new Question(element.getAsJsonObject().get("pergunta").getAsString(), answers.get(0),
                            List.copyOf(answers.subList(1, answers.size()))));
                }
            }
            chairs.put(entry.getKey(), new Chair(entry.getKey(), chair.get("titulo").getAsString(),
                    chair.has("sobre") ? chair.get("sobre").getAsString() : "", strings(array(chair, "runas")),
                    List.copyOf(lessons), List.copyOf(exam)));
        }
        Map<Course, CourseInfo> courses = new EnumMap<>(Course.class);
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("cursos").entrySet()) {
            Optional<Course> course = Course.byId(entry.getKey());
            if (course.isEmpty()) {
                LOGGER.warn("The curriculum names a course the house does not have: {}", entry.getKey());
                continue;
            }
            JsonObject info = entry.getValue().getAsJsonObject();
            List<String> listed = known(strings(array(info, "cadeiras")), chairs, entry.getKey());
            courses.put(course.get(), new CourseInfo(course.get(), info.get("titulo").getAsString(),
                    info.get("especialista").getAsString(), listed));
        }
        for (Course course : Course.values()) {
            if (!courses.containsKey(course)) {
                LOGGER.warn("The curriculum has nothing for the course {}", course.id());
            }
        }
        List<String> firstYear = known(strings(array(root, "primeiroAno")), chairs, "primeiroAno");
        return new Curriculum(firstYear, Collections.unmodifiableMap(courses), Collections.unmodifiableMap(chairs));
    }

    /** The chairs of {@code ids} the curriculum has; the others are told and left out. */
    private static List<String> known(List<String> ids, Map<String, Chair> chairs, String where) {
        List<String> found = new ArrayList<>();
        for (String id : ids) {
            if (chairs.containsKey(id)) {
                found.add(id);
            } else {
                LOGGER.warn("The curriculum's {} names a chair it does not have: {}", where, id);
            }
        }
        return List.copyOf(found);
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static List<String> strings(JsonArray array) {
        List<String> found = new ArrayList<>();
        array.forEach(element -> found.add(element.getAsString()));
        return List.copyOf(found);
    }
}
