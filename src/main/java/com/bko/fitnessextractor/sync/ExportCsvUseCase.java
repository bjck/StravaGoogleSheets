package com.bko.fitnessextractor.sync;

import java.io.IOException;

public interface ExportCsvUseCase {
    byte[] exportAllCsvZip() throws IOException;
}
