/**
 * Модель контенту: незмінні визначення ідеологій, доктрин, ресурсів тощо, зібрані в {@link
 * kolo.engine.content.ContentPack}.
 *
 * <p>Конструктори перевіряють кожне значення й кидають {@link kolo.engine.error.ValidationException}; завантажувач
 * YAML (модуль {@code content}) загортає їх у {@link kolo.engine.error.ContentException} з файлом і місцем помилки.
 */
package kolo.engine.content;
