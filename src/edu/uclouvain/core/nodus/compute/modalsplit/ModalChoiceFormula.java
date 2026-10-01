/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.compute.modalsplit;

import com.bbn.openmap.Environment;
import java.awt.Dimension;
import javax.swing.JLabel;

/**
 * Displays the probability and systematic utility actually fitted by the selected modal model.
 *
 * <p>Only available modes enter the probability formulas. Probit assumes independent N(0,1) errors,
 * so its joint choice probability is a one-dimensional normal integral. Symbol definitions belong
 * to the tooltip; the visible content contains only equations. All models reserve the same space to
 * keep the dialog stable when the selection changes.
 *
 * <p>For proportional choice, the primary formula shows the multiplicative adjusted cost {@code
 * k*C}. Its displayed utility {@code -ln(k)-ln(C)} is an equivalent representation: exponentiating
 * it yields {@code 1/(k*C)}. The logarithms neither introduce an additive physical cost nor add an
 * estimated cost exponent. The reference factor is one.
 *
 * <p>This is a presentation component with no estimation state or file access. Construct it and
 * change its method on the Swing event thread. Localized tooltips define the symbols; changing the
 * method preserves a preferred size large enough for every supported formula.
 */
final class ModalChoiceFormula extends JLabel {
  private static final long serialVersionUID = -9180100579347799093L;
  private static final String UTILITY =
      "V<sub>i</sub> = &#945;<sub>i</sub> + &#946; ln(C<sub>i</sub>),"
          + "&nbsp;&nbsp; &#945;<sub>r</sub> = 0";
  private static final String LOGIT =
      "P<sub>i</sub> = exp(V<sub>i</sub>) / " + "&#8721;<sub>j&#8712;A</sub> exp(V<sub>j</sub>)";
  private static final String PROBIT =
      "P<sub>i</sub> = &#8747;<sub>&#8722;&#8734;</sub><sup>+&#8734;</sup>"
          + " &#966;(z) &#8719;<sub>j&#8712;A, j&#8800;i</sub>"
          + " &#934;(z + V<sub>i</sub> &#8722; V<sub>j</sub>) dz";

  private static final String PROPORTIONAL =
      "P<sub>i</sub> = (k<sub>i</sub>C<sub>i</sub>)<sup>&#8722;1</sup> / "
          + "&#8721;<sub>j&#8712;A</sub> (k<sub>j</sub>C<sub>j</sub>)<sup>&#8722;1</sup>";
  private static final String PROPORTIONAL_UTILITY =
      "V<sub>i</sub> = &#8722;ln(k<sub>i</sub>) &#8722; ln(C<sub>i</sub>),"
          + "&nbsp;&nbsp; k<sub>r</sub> = 1";

  ModalChoiceFormula() {
    Dimension size = new Dimension();
    for (String method : new String[] {"Proportional", "MNP", "MNL"}) {
      setMethod(method);
      Dimension preferred = getPreferredSize();
      size.width = Math.max(size.width, preferred.width);
      size.height = Math.max(size.height, preferred.height);
    }
    setPreferredSize(size);
  }

  /**
   * Updates the equations and their localized symbol definitions.
   *
   * @param method short built-in model name, MNL, MNP or Proportional
   */
  void setMethod(String method) {
    boolean probit = "MNP".equals(method);
    boolean proportional = "Proportional".equals(method);
    setText(
        "<html><table cellpadding='2'><tr><td>"
            + (proportional ? PROPORTIONAL : probit ? PROBIT : LOGIT)
            + "</td></tr><tr><td>"
            + (proportional ? PROPORTIONAL_UTILITY : UTILITY)
            + "</td></tr></table></html>");
    if (proportional) {
      setToolTipText(
          Environment.getI18n()
              .get(
                  ModalChoiceFormula.class,
                  "tooltip.proportional",
                  "<html>A: available modes; C: cheapest admissible modal cost; r: reference mode."
                      + "<br>k: positive modal cost adjustment factor; the reference factor is one."
                      + "<br>Factors are estimated separately for each commodity group."
                      + "<br>The inverse-cost exponent is fixed at -1.</html>"));
      return;
    }
    setToolTipText(
        Environment.getI18n()
            .get(
                ModalChoiceFormula.class,
                probit ? "tooltip.probit" : "tooltip.logit",
                "<html>A: available modes; C: cheapest admissible modal cost; r: reference mode."
                    + "<br>V: systematic utility; alpha: modal intercept; "
                    + "beta: shared log-cost coefficient."
                    + "<br>Coefficients are estimated separately for each commodity group."
                    + (probit
                        ? "<br>phi and Phi: standard-normal density and cumulative distribution."
                            + "<br>Utility errors are independent N(0,1)."
                        : "")
                    + "</html>"));
  }
}
