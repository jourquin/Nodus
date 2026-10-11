# Dialog button localization audit

The application language now controls Swing's standard dialog buttons as well as
Nodus's own translated labels. In French, confirmations display **Oui**, **Non**
and **Annuler**, independently of the operating system's language.

## Scope and findings

The source audit covered all 289 Java files under `src/`, including the customized
OpenMap classes. It found 174 `JOptionPane` dialog calls in 49 source files:

| Dialog API | Calls |
| --- | ---: |
| Confirmation | 34 |
| Message | 113 |
| Input | 11 |
| Explicit option dialog | 12 |
| Direct option-pane construction | 4 |

Counts describe the source after the fixes. Standard dialogs share Swing defaults;
custom option dialogs supply translated labels. The audit also checked 55 distinct
French translation keys for standard/custom dialog button captions, and searched
button constructors and text assignments for hard-coded action captions.

The modal-parameter overwrite confirmation relied on Swing's implicit Yes/No
labels. Changing the JVM/OpenMap locale did not synchronize Swing's component
locale and UI defaults, allowing English buttons alongside French messages.
`LocaleUtils.applyLocale` now updates all three. Standard dialog labels and
mnemonics come from application-owned `SwingMessages` resources with English and
French translations, including regional French locales. `Nodus.setLookAndFeel`
reapplies them after selecting a look and feel.

Additional fixes:

- Global Preferences: translate its explicit OK and Cancel buttons.
- Font chooser: translate its explicit OK and Cancel buttons while retaining the
  existing action commands.
- System Information: translate its Close button.
- Rendering-scale threshold confirmation: supply local Yes/Reset/Cancel options
  instead of temporarily changing the global No-button label. Return-value
  meanings remain unchanged.

The other custom option arrays already use translated captions. The remaining
literal `JButton` captions are the language-independent `+` and `-` zoom controls.
The audit concerns dialog button localization; it does not claim every message or
all controls in external library code have been translated.

## Verification

`LocaleUtilsTest` constructs real Swing option panes on the event-dispatch thread,
without opening windows. It checks all standard option combinations, French after
English Swing initialization, regional French, switching back to English, unchanged
Yes/No return values, custom options and supported installed look and feels.

The implementation applies to newly created dialogs. Existing custom windows may
still need reopening after a language change, as the language chooser already notes.
