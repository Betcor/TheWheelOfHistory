import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Спільний для всієї збірки замок задач `budgetTest`: зареєстрований з `maxParallelUsages = 1`, він не дає бюджетам
 * різних модулів міряти час одночасно й ділити процесор (при `org.gradle.parallel`). Стану не має.
 */
abstract class BudgetTestLock : BuildService<BuildServiceParameters.None>
