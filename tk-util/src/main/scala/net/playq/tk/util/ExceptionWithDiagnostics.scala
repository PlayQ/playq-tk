package net.playq.tk.util

abstract class ExceptionWithDiagnostics(val message: String, val cause: Throwable, val diagnostics: Option[String]) extends RuntimeException(message, cause)
