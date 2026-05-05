package d4s

package object util {

  def leftpadLongInvertNegative(long: Long): String = {
    val str = leftpadLong(long)
    // negate only digits, not `-`, such that:
    //   - all negative numbers are less than all positive numbers because '-' < '0'...'9'
    //   - aside from '-', negative numbers order is reversed – smaller digits = greater value (as expected)
    if (long < 0) negateDigits(str) else str
  }

  def leftpadIntInvertNegative(int: Int): String = {
    val str = leftpadInt(int)
    if (int < 0) negateDigits(str) else str
  }

  def reinvertNegative(str: String): String = {
    if (str.startsWith("-")) negateDigits(str) else str
  }

  def negateDigits(str: String): String = {
    str.map {
      case '0' => '9'
      case '1' => '8'
      case '2' => '7'
      case '3' => '6'
      case '4' => '5'
      case '5' => '4'
      case '6' => '3'
      case '7' => '2'
      case '8' => '1'
      case '9' => '0'
      case chr => chr
    }
  }

  def negateMinus(str: String): String = {
    str.map {
      case '-' => 'Z'
      case 'Z' => '-'
      case chr => chr
    }
  }

  def leftpadLong(long: Long): String = {
    f"$long%021d" // one more character than in Long.MinValue, which has 20 characters - which is one more than Long.MaxValue's 19 digits.
  }

  def leftpadInt(int: Int): String = {
    f"$int%012d" // one more character than in Int.MinValue, which has 11 characters - which is one more than Long.MaxValue's 10 digits.
  }

}
