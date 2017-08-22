// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem.math

import cats.tests.CatsSuite
import cats.{ Eq, Show, Order }
import cats.kernel.laws._
import gem.arb._

@SuppressWarnings(Array("org.wartremover.warts.ToString", "org.wartremover.warts.Equals"))
final class RightAscensionSpec extends CatsSuite {
  import ArbRightAscension._

  // Laws
  checkAll("RightAscension", OrderLaws[RightAscension].order)

  test("Equality must be natural") {
    forAll { (a: RightAscension, b: RightAscension) =>
      a.equals(b) shouldEqual Eq[RightAscension].eqv(a, b)
    }
  }

  test("Order must be consistent with .toHourAngle.toMicroarcseconds") {
    forAll { (a: RightAscension, b: RightAscension) =>
      Order[Long].comparison(a.toHourAngle.toMicroarcseconds, b.toHourAngle.toMicroarcseconds) shouldEqual
      Order[RightAscension].comparison(a, b)
    }
  }

  test("Show must be natural") {
    forAll { (a: RightAscension) =>
      a.toString shouldEqual Show[RightAscension].show(a)
    }
  }

  test("Conversion to HourAngle must be invertable") {
    forAll { (a: RightAscension) =>
      RightAscension.fromHourAngle(a.toHourAngle) shouldEqual a
    }
  }

  test("format and unformat must round-trip") {
    forAll { (a: RightAscension) =>
      RightAscension.unformat(a.format) shouldEqual Some(a)
    }
  }

}
