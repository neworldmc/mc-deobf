import java.io.IOException;
import java.util.List;

public interface MappingParser {
    public List<Mapping.ClassMapping> parse() throws IOException, InvalidInputException;
}
