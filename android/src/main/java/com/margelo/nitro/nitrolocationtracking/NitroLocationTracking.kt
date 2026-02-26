package com.margelo.nitro.nitrolocationtracking
  
import com.facebook.proguard.annotations.DoNotStrip

@DoNotStrip
class NitroLocationTracking : HybridNitroLocationTrackingSpec() {
  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }
}
