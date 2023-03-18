package utils

object DateUtil {
  def stringDateToEpochSecond(date: String): Long = {
    java.time.Instant.parse(date).getEpochSecond
  }
}
