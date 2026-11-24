function languageSelection(saved, selected, locales) {
    const language = [saved, selected, 'en'].find(value => value === 'ar' || value === 'en');
    return {language, messages: {...locales.en, ...locales[language]}, direction: language === 'ar' ? 'rtl' : 'ltr'};
}
module.exports = {languageSelection};
