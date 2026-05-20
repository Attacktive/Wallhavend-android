package xyz.attacktive.wallhavend.domain.model

class NoResultsException: Exception("No wallpapers found for the current query")
class UnsupportedFormatException(mimeType: String): Exception("Unsupported image format: $mimeType")
