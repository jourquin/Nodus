# Modal choice estimation in Nodus

The built-in **Multinomial logit** modal choice method uses the utility from the R and Biogeme
demo. Nodus estimates its parameters directly, without SQL/Groovy scripts or an external statistics
package. Parameter estimation and demand assignment are separate operations.

## Estimate parameters, then assign

1. Open **Project → Modal choice estimation…** and choose **Multinomial logit**,
   **Multinomial probit**, or **Proportional**.
2. Select **Source cost functions**. This existing file must contain the network's transport costs.
   Calibration uses its base definitions, with commodity/class overrides; numbered scenario
   overrides are ignored. No scenario results are created or overwritten.
   **Save cost functions as** initially proposes the source filename. Keep it to update that file,
   or enter another target name. Output files are saved in the project directory.
3. Associate each mode ID with its observed modal OD table. Include every mode available to
   calibration traffic. Choose a reference mode: its
   intercept is fixed at zero for logit/probit, or its cost factor at one for proportional choice.
   Tables need `grp`, `org`, `dst`, and `qty`; an optional `class` column defaults to zero.
   Missing rows and SQL NULL quantities count as zero. Duplicate records are summed.
4. Choose fast or exact multi-flow routing, maximum detour and worker threads. Calibration always
   uses one route search per mode/means, without cost markup. The detour is a ratio (zero disables
   its limit). **Log estimation details to terminal** optionally prints skipped OD records,
   coverage statistics and fitted parameters. It is off by default and remembered per project;
   the report is always saved in the cost file after successful estimation.
5. Click **Estimate**. Nodus computes available modal route costs, estimates each commodity group's
   model, and saves coefficients and diagnostics in the named output cost file. It stops there.
   The main window shows routing progress and an animated activity indicator during data
   preparation and fitting, whose duration is unknown. The status identifies the group being fitted.
   Cancellation remains available throughout. The activity indicator is the usual progress bar
   with a moving segment, not the mouse pointer.
6. Later, open **Project → Assignment**. Select the calibrated cost file, the matching modal-choice
   method, and the OD matrix to assign (observed total demand, a forecast, or another scenario).
   Click **Assign**. No parameter estimation runs during assignment.

Estimation preferences are saved at project level, independently of assignment scenarios. Existing
observed-table mappings from the former combined workflow are recovered when available; its
estimation and summed-demand flags are ignored. Existing cost-file coefficients remain usable.
The Assignment dialog always uses its selected OD matrix and its own SQL/geographic filters.
There is no implicit demand replacement or calibration-based filtering of assignments.

The internal cost-computation pass uses the sum of the observed modal quantities to discover the
union of OD records. It allocates `total quantity / number of available modes`, with equal route
shares within each mode, and records the cheapest admissible modal costs regardless of assigned
quantity. This computation is necessary to fit each model; it publishes no path or virtual-network
result tables and does not run assignment scripts. Its temporary demand table is removed on success,
failure or cancellation. Original observed matrices and existing scenario results are preserved.

The bottom of the dialog displays the selected model's probability and systematic utility
equations under **Model specification:**. They update immediately when switching models. Hover over the equations
for symbol definitions, or over a control for its purpose and effect.

The dialog, observed-data editor, settings, calibration workflow and cost-file writer live in
`edu.uclouvain.core.nodus.compute.modalsplit`, beside the modal methods and estimators.

The logit model is:

```text
U(OD, mode, group) = intercept(mode, group) + beta(group) * log(cost(OD, mode))
P(OD, mode, group) = exp(U) / sum over available modes of exp(U)
```

The reference mode's intercept is zero, and the log-cost coefficient is shared by all modes within
each group. The final assignment distributes each mode's flow among its routes proportionally to
inverse route cost, as in the demo plugin. Cost files use `(intercept).<mode>.<group>`,
`log(cost).<mode>.<group>` and `mnl.reference.<group>`. Existing demo coefficient files can also be
used; when no reference is specified, the lowest parameterized mode is the reference.

**Scope and migration:** this implementation supports static, unimodal alternatives. An intermodal
route or a feasible mode without configured observations is reported as an error. Available route
costs must be finite and positive. Positive observed flow without an admissible route causes the
entire group/origin/destination/class observation to be skipped from estimation, across all modes.
Each skipped record is printed in the terminal with its missing modes, observed quantities and
total excluded quantity. A summary reports how many records were skipped and retained; it is also
included in the cost file's estimation report. No error dialog is shown for these missing routes.

The `calibration coverage` lines report totals for all observations, each commodity group, each
mode, and each group/mode combination:

- **Observed quantity**: the original quantity before excluding any records.
- **Excluded quantity**: all quantity removed from estimation with skipped OD records, across
  missing and routable modes. Its percentage is `100 * excluded quantity / observed quantity` for
  the listed scope. It does not change assignment demand.
- **Unroutable quantity**: only the observed quantity whose mode has no admissible route, with its
  percentage of the same observed total.
- **Other excluded quantity**: the difference between excluded and unroutable quantities; this
  demand has a route but belongs to an OD record excluded from estimation.

Record counts are by group/origin/destination/class. Mode-specific counts include only records
with positive observed demand for that mode, so records can appear under several modes; they must
not be added across modes. Quantities can be added across modes without double counting. A mode
with no observed demand shows `n/a` percentages. Reports use all observed calibration demand, not
the separate forecast matrix or the final assignment's filters. They appear in the terminal even
if estimation subsequently fails, and are saved as comments in the cost file when estimation
succeeds. Rerun calibration after rebuilding to obtain these diagnostics for an existing project.

Exclusions affect only parameter estimation. For example, an observation with 80 road tonnes and
20 waterway tonnes, but only a road route, is excluded from the fit. A later assignment of a selected
OD matrix containing 100 tonnes for that record can allocate all 100 tonnes to road. To assign only
a chosen subset, explicitly select an appropriate OD matrix or assignment filter. The estimator
neither constructs a persistent assignment matrix nor changes one.

Records with no observed flow in an unavailable mode remain eligible. If all observations are
skipped, Nodus reports this in the terminal and stops without changing coefficients or previous
assignment results. The remaining data must still be sufficient to estimate the chosen model;
other estimation errors are reported normally. Check the skipped records before interpreting a
fit on this reduced population.

If a commodity group has no MNL parameters, assignment displays one warning and uses the former
built-in MNL utility `V = -cost`: a cost factor of **1** and modal constants of **0**. Its route
shares also retain the former exponential rule, proportional to `exp(-route cost)` within each
mode. No defaults are written to the cost file. Groups with saved coefficients use the fitted
log-cost model; incomplete or invalid coefficients still produce an error. Custom modal split
plugins remain available.

All commodity groups must fit successfully before the output cost file is created or replaced.
The source's other cost expressions and comments are retained; estimates for groups not recalibrated
are retained too. Saving under another name leaves the source unchanged. Changes to either file
during estimation abort saving instead of overwriting those edits.
Cancellation or an estimation failure leaves both cost files and prior assignment results
untouched. After a successful save, estimates remain available for any later assignments. The generated comment block reports convergence, log-likelihoods
and conventional standard errors.

## Estimate a multinomial probit

Choose **Multinomial probit** in the estimation dialog and subsequently in Assignment. It uses
exactly the same cost variable, observed matrices, availability rules and reference-mode convention
as logit. The former built-in Abraham method and its exponent estimator have been removed.

```text
V(OD, mode, group) = intercept(mode, group) + beta(group) * log(cost(OD, mode))
U(OD, mode, group) = V(OD, mode, group) + epsilon(mode)
epsilon(mode) are independent N(0,1)
P(mode i) = Pr(U_i > U_j for every other available mode j)
         = integral phi(z) * product over j != i of Phi(z + V_i - V_j) dz
```

Here `phi` and `Phi` are the standard-normal density and cumulative distribution. With two
available modes, this reduces to `Phi((V_i - V_j) / sqrt(2))`. With more modes, Nodus evaluates the
joint probability through deterministic one-dimensional quadrature. It does not normalize separate
pairwise probits and does not use random draws. Log probabilities and analytical derivatives of
the integral support stable estimation of small shares; quadrature is refined and checked before
accepting a result. This costs more computation than logit's closed-form probabilities.

Each group has one shared log-cost coefficient and one intercept per mode except the reference,
whose intercept is zero. The errors' variance is fixed at one to identify coefficient scale.
This initial specification does **not** estimate correlations or mode-specific variances.
It is an independent-error multinomial probit, rather than a general covariance model.
Coefficients are unconstrained as for logit; inspect the fitted cost coefficient's sign.

Probit coefficients have their own cost-file keys:

```text
probit.(intercept).<mode>.<group> = <intercept>
probit.log(cost).<mode>.<group> = <shared coefficient>
probit.reference.<group> = <reference mode ID>
```

Logit and probit coefficients cannot be interchanged: their error distributions and scales differ.
Estimating one preserves the other's coefficients and report, as well as estimates for other groups.
All groups must fit before any coefficients are saved. Both fitted models use inverse route cost
to split each mode's assigned flow among its routes; this route allocation is not estimated from
modal OD data.

If a group has no probit parameters, assignment warns once and uses `V = -cost`, with cost factor
**1**, modal constants **0**, and independent normal errors of variance **1**. As with default MNL,
route allocation is exponential. MNL or proportional parameters in the same file do not supply
probit coefficients, and missing probit parameters do not prevent using this default model.

## Estimate proportional cost adjustment factors

Choose **Proportional** in the estimation dialog and later in Assignment. It estimates a positive
factor `k(mode, group)` for each mode and commodity group, fixing the reference factor at **1**:

```text
Adjusted modal cost = k(mode, group) * cheapest admissible modal cost
P(mode) = [1 / (k(mode, group) * cost(mode))] / sum over available modes [1 / (k * cost)]
V(mode) = -ln(k(mode, group)) - ln(cost(mode))
```

The inverse-cost exponent remains **-1**. Estimation fits logit intercepts `alpha = -ln(k)` with a
fixed log-cost coefficient, then exports the positive factors. A factor of 1.5 makes the mode behave
as though its cost were 50% higher relative to the reference scale; 0.8 means 20% lower. Factors may
also reflect omitted attributes such as reliability. They adjust modal shares only; the physical
network costs and within-mode inverse-cost route allocation are unchanged.

```text
proportional.costFactor.<mode>.<group> = <positive factor>
proportional.reference.<group> = <reference mode ID>
```

These keys are independent of logit/probit coefficients, which are preserved along with their reports.
Re-estimation replaces factors for the fitted groups, preserving other groups. Groups with no
proportional entries retain the original inverse-cost method, with all factors equal to one. A group
with proportional entries must provide every available mode's factor and a reference factor of one;
partial or invalid entries produce an error instead of silently using defaults.

The calibration report contains factors, approximate standard errors obtained by the delta method,
and the likelihood before adjustment (all `k=1`). Standard errors use observed quantities as frequency
weights. Unlike the logit with an estimated cost coefficient, proportional factors can be identified
with constant cost ratios or equal costs. Connected choice sets and sufficient observed demand remain
necessary; zero-demand modes, separation and unidentified factors are rejected before saving.
Unavailable modes with zero observed flow are allowed; positive observed flow on an unavailable mode
excludes that entire OD record under the same coverage rules as logit/probit. Calibrated proportional
choice requires unimodal routes; uncalibrated legacy proportional assignments retain intermodal support.

The Java API is `ProportionalEstimator.estimate(costs, quantities, referenceColumn)`; its result
provides `getCostFactors()`, `getCostFactorStandardErrors()` and `toCostFileEntries(modeIds, group)`.


## Cost evaluation and routing

The chosen cost file supplies calibration costs. Base functions, commodity/class overrides and
generic node rules apply. Numbered scenario overrides are ignored, so the currently selected
assignment scenario and old calibration preferences cannot silently change a fit. If an override
is needed for calibration, put the desired expression in the base definitions of the selected
cost file. Calibration leaves existing numbered overrides and assignment preferences untouched.

Route costs stay in memory; the scratch demand table is dropped afterward. Calibration creates
no persistent assignment-result tables. Its persistent outputs are the fitted coefficients and
diagnostic comments in the named output cost file.

Calibration performs **one route search per mode/means**, with **no cost markup**. For each OD
record and mode, all models use the cheapest admissible cost found, including loading, moving and
unloading costs. It does not compute additional alternative routes or average their costs. If two
means of the same mode produce costs 10 and 12, estimation uses **10**. Detour restrictions still
apply; if the first route is rejected, no second search is made to find a substitute for it.

The later assignment retains its own routing iterations and markup. Additional routes can affect
route-level flows without changing modal shares. Use cost and availability assumptions consistent
with the data used for calibration.

## Estimator API

`LogCostLogitEstimator` and `LogCostProbitEstimator` are Java 11 classes with no additional
dependencies. Both accept the same cost/quantity arrays; probit returns `LogCostChoiceEstimate`.
Developers can call them directly from Java or Groovy, as illustrated in the
[Java API example](#java-api). For estimation from a Nodus project, use
**Project → Modal choice estimation…** to compute route costs and update the selected cost file.

### Data and statistical conventions

Each input row is one OD pair. Estimation maximizes
`sum(OD, mode) quantity(OD, mode) * log(P(OD, mode))`. This is equivalent to weighting the
share-based likelihood by the row's total quantity, as in the Biogeme script. It uses quantities
directly, without expanding flows into individual observations. Quantities may be fractional.

- SQL NULL quantities become zero; negative or nonfinite quantities are rejected.
- In the standalone estimator API, missing (NaN), zero or negative costs mark an unavailable mode,
  which is excluded from the denominator. Infinite costs and positive observed flow for unavailable
  modes are rejected. In the integrated workflow,
  absence of an admissible route defines unavailability; a routed mode with an invalid cost is an
  error. Positive observed flow for an unavailable mode excludes the entire observation as described
  above, with a terminal diagnostic.
- Rows with zero total quantity are ignored. Zero quantities on individual available modes are valid.
- Standard errors use the inverse observed information and treat quantities as frequency weights.
  They are not robust/clustered errors. Rescaling all quantities leaves coefficients unchanged,
  multiplies the log-likelihood by the same factor and divides standard errors by its square root.
- For logit/probit, the zero-parameter likelihood uses equal shares among the available modes. It is not a separately
  estimated intercept-only model.

The R example replaces zero flows with `0.001` and missing costs with a large cost. That changes the
estimation data; its coefficients need not exactly match estimates using true availability and zero
flows. Moreover, R's `mlogit` normalizes the weights in this workflow: compare likelihoods and
standard errors only after matching the effective weight total. Biogeme's `-1000` unavailable utility
is a numerical approximation; this Java estimator excludes unavailable modes exactly.

The solver uses analytical derivatives and damped Newton steps, with scaled log-cost differences,
stable logit exponentials and probit normal integrals. Unidentified models (for example, constant cost ratios when fitting the cost coefficient, disconnected choice
sets or a mode with no observed flow), singular information and nonconvergence produce descriptive
exceptions. It does not add ridge penalties or impose a negative cost coefficient. Inspect the sign
and fit before using an estimate. Perfect or near separation can prevent finite, reliable estimates;
the convergence checks are not a general separation test.

### Java API

```java
import edu.uclouvain.core.nodus.compute.modalsplit.LogCostLogitEstimator;

double[][] costs = { {1, 1}, {1, 2} };
double[][] quantities = { {2, 3}, {8, 3} };
LogCostLogitEstimator.Result result = LogCostLogitEstimator.estimate(costs, quantities);
// intercepts = [0, log(1.5)], beta = -2
System.out.println(result.toCostFileEntries(new int[] {1, 2}, 0));
```

The overload with a third argument selects the zero-based reference column. Result arrays are
defensive copies. Column positions are mapped to Nodus mode IDs explicitly when exporting.

### Why a small embedded estimator?

Nodus is GPL-3.0-or-later. [Weka's Logistic class](https://weka.sourceforge.io/doc.dev/weka/classifiers/functions/Logistic.html)
implements multinomial logistic regression and weighted instances under
[GPLv3](https://github.com/Waikato/weka-3.8/blob/master/weka/pom.xml). However, its usual classifier
parameterization estimates a separate coefficient vector for each class. The demo needs
alternative-specific costs, a shared cost coefficient and per-row availability, so Weka is not a
direct substitute without additional constraints or custom modeling.

[Apache Commons Math](https://commons.apache.org/proper/commons-math/userguide/optimization.html)
offers general optimizers rather than this ready-made choice model. Its
[Apache 2.0 license](https://commons.apache.org/proper/commons-math/userguide/overview.html)
is [compatible with GPLv3](https://www.gnu.org/licenses/license-list.html#apache2).
For this small linear-in-parameters model, the embedded estimator avoids an additional library and
keeps the likelihood and availability rules explicit. It is distributed under Nodus's existing GPL
license. R and Biogeme remain useful for richer specifications such as nested or mixed logit.

### Verification

Run `ant -f build-tests.xml '-Dtest.includes=**/Log*Test.class,**/Probit*Test.class,**/MultinomialProbitTest.class,**/ModalSplitTest.class' Test`.
The tests cover analytical solutions, a weighted R reference, missing alternatives, changing the
reference mode, quantity/cost scaling, invalid data, singular models, separation and cost-file export.
End-to-end tests exercise both routing methods with H2 and HSQLDB,
multiple commodity groups, observed and forecast demand, coefficient reuse, cancellation, invalid
observations, preservation of existing results after successful estimation, logging and skipping missing routes (including distinct OD classes and multiple missing
modes), and preservation of existing files and assignment results when no usable records remain. Probit
checks include independent SciPy quadrature/optimization references, normal-tail probabilities,
likelihood derivatives, known-coefficient recovery and standard errors, plus the complete calibration
and assignment workflow on both databases. SciPy is not required to run the Java tests.

The R reference uses the six cost/quantity rows in `LogCostLogitEstimatorTest`, expanded as in
`MLogit.R`, with `mode ~ log(cost) | 1 | 1`, `weights = tons`, and
`tol = ftol = steptol = 1e-14`. R gives intercepts `0.030331521099273` and `-0.137361127842608`,
and slope `-1.723605031702693`. The original weights sum to 345; the expanded data has 18 rows.
R's log-likelihood is `-16.45950925163003`; multiplying by `345/18` gives the Java likelihood
`-315.473927322909`. Conventional standard errors were also checked with R's `optim` numerical
Hessian on the directly weighted likelihood. R is not needed to run the Java tests.

## External estimation examples

- The explanatory variable (cost) is gathered from an uncalibrated multimodal assignment, i.e., the total travel cost for all the
modes and origin-destination pairs. This information is read by the "CreateMLogitInput.groovy" script from the assignment "header" table, 
along with the expected quantities for each mode (in the modal OD matrixes). The result is written in the "mlogit_input" table.

- The estimators are then computed using by the "MLogit.R" script. To run it, [R](https://www.r-project.org/) must be installed on 
your computer, along with the [RJDBC](https://cran.r-project.org/package=RJDBC) and
[mlogit](https://cran.r-project.org/package=mlogit) packages. The script produces the "mlogit.txt" file, containing the output of
the estimated models (one for each group of commodities). The estimators are also stored in "mlogit_coefs.txt". 
The content of this file can be cut & pasted in a Nodus cost file. 

- The MLogit.java file contains the source code of a user defined modal split method that uses these estimators. It can be compiled 
to generate the MLogit.jar file (which can already be found in the "demo" project directory). This plugin reads the estimators 
stored in the Nodus cost file and applies them to compute the utility of each alternative mode and to estimate their modal share. 

- The logit model can also be solved using the [Biogeme](https://biogeme.epfl.ch) toolbox. This is illustrated by the "MLogit.py" 
[Python](https://www.python.org) script. It reads the same "wide format" "mlogit_input" table as the R script and estimates the model
with one row per origin-destination pair and commodity group. The observed modal quantities are converted to modal shares inside the
script. The Python script requires the packages listed in "Python-requirements.txt". From the "demo/MLogit" directory, they can be installed
with: ```sh python3 -m pip install -r Python-requirements.txt ```
