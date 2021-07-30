package net.playq.tk.postgres.exceptions

import net.playq.tk.util.ExceptionWithDiagnostics

sealed abstract class SQLConnectorException(message: String, cause: Throwable, diagnostics: Option[String]) extends ExceptionWithDiagnostics(message, cause, diagnostics)

final class SQLQueryException(message: String, cause: Throwable, diagnostics: Option[String]) extends SQLConnectorException(message, cause, diagnostics)

final class SQLTimeoutException(message: String) extends SQLConnectorException(message, null, None)
