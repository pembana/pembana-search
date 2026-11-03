/*
 * Copyright 2026 Pembana Search contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pembana.search.samples;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.pembana.search.core.TextPattern;

/**
 * Consumer-owned JDBC example over the plain_text_row table's title and note columns.
 * Only bound pattern data comes from input; the consumer owns the SQL and connection.
 *
 * @author Gunnar Hillert
 */
public final class PlainTextJdbcConsumer {

	private PlainTextJdbcConsumer() {
	}

	/**
	 * Matches all patterns against either column using the library's LIKE adapter.
	 * @param connection borrowed connection, left open and in its current transaction
	 * @param patterns composed words or one whole-value pattern; empty is neutral
	 * @param sensitive whether to preserve case
	 * @return matching identifiers in ascending order
	 * @throws SQLException if the database query fails
	 */
	public static List<Long> ids(Connection connection, List<TextPattern> patterns, boolean sensitive)
			throws SQLException {
		String fields = sensitive ? "title like ? escape '!' or note like ? escape '!'"
				: "lower(title) like ? escape '!' or lower(note) like ? escape '!'";
		String where = patterns.isEmpty() ? "1=1"
				: String.join(" and ", Collections.nCopies(patterns.size(), "(" + fields + ")"));
		try (var statement = connection
			.prepareStatement("select id from plain_text_row where " + where + " order by id")) {
			int index = 1;
			for (TextPattern pattern : patterns) {
				statement.setString(index++, pattern.sqlLike(sensitive));
				statement.setString(index++, pattern.sqlLike(sensitive));
			}
			try (var rows = statement.executeQuery()) {
				List<Long> result = new ArrayList<>();
				while (rows.next()) {
					result.add(rows.getLong(1));
				}
				return result;
			}
		}
	}

}
