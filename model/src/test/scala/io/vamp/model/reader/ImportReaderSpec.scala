package io.vamp.model.reader

import io.vamp.model.notification.{ EmptyImportError, ImportDefinitionError }
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ImportReaderSpec extends ReaderSpec {

  "ImportReader" should "read the single import" in {
    ImportReader.read(res("template/template1.yml")) shouldBe
      Import(Map("name" → "template1"), List(ImportReference("templateA", "templates")))
  }

  it should "read the multiple imports" in {
    ImportReader.read(res("template/template2.yml")) shouldBe
      Import(Map("name" → "template2"), List(ImportReference("templateA", "templates"), ImportReference("templateB", "templates")))
  }

  it should "throw an error when reference is not a string" in {
    expectedError[ImportDefinitionError.type] {
      ImportReader.read(res("template/template3.yml"))
    }
  }

  it should "read the multiple imports with kind" in {
    ImportReader.read(res("template/template4.yml")) shouldBe
      Import(Map("name" → "template4"), List(ImportReference("templateA", "templates"), ImportReference("sava", "breeds")))
  }

  it should "throw an error when reference is empty" in {
    expectedError[EmptyImportError.type] {
      ImportReader.read(res("template/template5.yml"))
    }
  }

  it should "throw an error when reference is not correct (kind/name)" in {
    expectedError[ImportDefinitionError.type] {
      ImportReader.read(res("template/template6.yml"))
    }
  }
}
