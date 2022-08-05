package org.arjunaoverdrive.app.search;

import java.util.List;

public interface SearchHelper {

    List<FoundPage> getFoundPages(int limit, int offset);
}
