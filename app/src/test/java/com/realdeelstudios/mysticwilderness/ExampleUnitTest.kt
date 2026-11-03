package com.realdeelstudios.mysticwilderness

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun fleksWorld_familyCreation() {
    var fam: com.github.quillraven.fleks.Family? = null
    val world = com.github.quillraven.fleks.configureWorld {
      fam = com.github.quillraven.fleks.World.family {
        all(
          com.realdeelstudios.mysticwilderness.ecs.components.DroppedItemTag,
          com.realdeelstudios.mysticwilderness.ecs.components.Position,
          com.realdeelstudios.mysticwilderness.ecs.components.ItemStackComponent,
          com.realdeelstudios.mysticwilderness.ecs.components.DroppedItemMetadata
        )
      }
    }
    assertNotNull(fam)
    assertEquals(0, fam!!.numEntities)
  }
}
