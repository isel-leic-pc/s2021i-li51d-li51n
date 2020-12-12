package pt.isel.pc.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CompositeThrowable extends Exception {

    private final List<Throwable> throwables;

    public CompositeThrowable(String message, List<Throwable> throwables) {
        super(message);
        this.throwables = throwables;
    }

    public static CompositeThrowable make(Throwable... exceptions) {
        List<Throwable> throwableList = Arrays.stream(exceptions)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
        String message = throwableList.stream()
          .map(Throwable::getMessage)
          .collect(Collectors.joining());
        return new CompositeThrowable(message, throwableList);
    }

    public List<Throwable> getThrowables() {
        return throwables;
    }
}
