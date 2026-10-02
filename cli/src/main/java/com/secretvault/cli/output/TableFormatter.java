package com.secretvault.cli.output;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Clean, lightweight terminal table formatter with dynamic column sizing.
 */
public class TableFormatter {

    private final List<String> headers;
    private final List<List<String>> rows = new ArrayList<>();

    public TableFormatter(String... headers) {
        this.headers = Arrays.asList(headers);
    }

    public void addRow(String... cells) {
        List<String> row = new ArrayList<>();
        for (String cell : cells) {
            row.add(cell != null ? cell : "");
        }
        rows.add(row);
    }

    public String render() {
        if (headers.isEmpty() && rows.isEmpty()) {
            return "";
        }

        int colCount = headers.size();
        for (List<String> row : rows) {
            if (row.size() > colCount) {
                colCount = row.size();
            }
        }

        int[] colWidths = new int[colCount];
        for (int i = 0; i < headers.size(); i++) {
            colWidths[i] = Math.max(colWidths[i], headers.get(i).length());
        }

        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                colWidths[i] = Math.max(colWidths[i], row.get(i).length());
            }
        }

        StringBuilder sb = new StringBuilder();

        // Header
        for (int i = 0; i < colCount; i++) {
            String title = (i < headers.size()) ? headers.get(i) : "";
            sb.append(String.format("%-" + (colWidths[i] + 3) + "s", title));
        }
        sb.append("\n");

        // Separator
        for (int i = 0; i < colCount; i++) {
            sb.append("-".repeat(colWidths[i])).append("   ");
        }
        sb.append("\n");

        // Rows
        for (List<String> row : rows) {
            for (int i = 0; i < colCount; i++) {
                String val = (i < row.size()) ? row.get(i) : "";
                sb.append(String.format("%-" + (colWidths[i] + 3) + "s", val));
            }
            sb.append("\n");
        }

        return sb.toString().stripTrailing();
    }
}
