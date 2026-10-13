package org.jellyfin.androidtv.data.contentwarning

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ContentWarningClassifierTests : FunSpec({
	test("topics are mapped to the app categories") {
		ContentWarningClassifier.categorize("there are sexual content", null) shouldBe ContentWarningCategory.SEX_NUDITY
		ContentWarningClassifier.categorize("someone is raped onscreen", null) shouldBe ContentWarningCategory.SEX_NUDITY
		ContentWarningClassifier.categorize("someone uses drugs", null) shouldBe ContentWarningCategory.DRUGS
		ContentWarningClassifier.categorize("someone says a slur", null) shouldBe ContentWarningCategory.LANGUAGE
		ContentWarningClassifier.categorize("there are jump scares", null) shouldBe ContentWarningCategory.FRIGHTENING
		ContentWarningClassifier.categorize("a dog dies", null) shouldBe ContentWarningCategory.VIOLENCE
		ContentWarningClassifier.categorize("someone is aware", null) shouldBe ContentWarningCategory.OTHER
	}

	test("severity grows with the number of confirmed topics") {
		fun topics(vararg names: String) = names.map { ContentWarningTopic(it, 5, 1) }

		ContentWarningClassifier.severity(emptyList()) shouldBe ContentWarningSeverity.NONE
		ContentWarningClassifier.severity(topics("a dog dies")) shouldBe ContentWarningSeverity.MILD
		ContentWarningClassifier.severity(topics("a dog dies", "someone is shot")) shouldBe ContentWarningSeverity.MODERATE
		ContentWarningClassifier.severity(topics("a", "b", "c", "d")) shouldBe ContentWarningSeverity.SEVERE
		ContentWarningClassifier.severity(topics("there is torture")) shouldBe ContentWarningSeverity.MODERATE
	}

	test("timestamps are extracted from scene descriptions") {
		ContentWarningClassifier.extractTimestamps("At 1:23:45 and again around 12:05.") shouldBe listOf("1:23:45", "12:05")
		ContentWarningClassifier.extractTimestamps("About 45 min in the dog is hurt") shouldBe listOf("45 min")
		ContentWarningClassifier.extractTimestamps("No time given") shouldBe emptyList()
	}
})

