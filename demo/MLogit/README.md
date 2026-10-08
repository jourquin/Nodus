# Modal choice estimation in Nodus

Nodus can estimate **Multinomial logit**, **Multinomial probit**, and **Proportional** modal
choice parameters directly from observed modal OD tables. The R and Biogeme scripts in this
directory are optional examples of the earlier external workflow; they are not needed for the
built-in estimator. Estimation and demand assignment are separate operations.

## Run the built-in workflow with the demo project

1. Open [`demo.nodus`](../demo.nodus), then choose **Project → Modal choice estimation…**.
   Select **Multinomial logit**, **Multinomial probit**, or **Proportional**.
2. Under **Source cost functions**, select an existing project `.costs` file, such as
   [`uncalibrated.costs`](../uncalibrated.costs). This file supplies the network costs and will
   receive a reference to the estimated parameter table. The dialog has no separate output-file
   field. Calibration uses base definitions and commodity/class overrides, but ignores numbered
   scenario overrides.
3. In the observed-table editor, map the demo's mode 1 to `od_road`, mode 2 to `od_iww`, and mode 3
   to `od_rail`. Select one reference mode. Its intercept is zero for logit/probit, or its cost
   factor is one for proportional choice. Each observed table needs `grp`, `org`, `dst`, and `qty`;
   an optional `class` column defaults to zero. Missing rows and SQL NULL quantities count as
   zero, and duplicate records are summed.
4. Optionally enter a **Merged OD table** name to save the sum of those modal quantities by group,
   origin, and destination for later use in Assignment. Leave it blank to create no assignment
   demand table. The merge does not retain an OD `class` column. The name must differ from the
   source OD tables and the parameter table.
5. Choose fast or exact multi-flow routing, maximum detour ratio, and worker threads. Zero detour
   disables its limit. Optionally enable **Estimate pivots** and set the maximum absolute pivot
   (default 8). These bounded mode/OD/group utility corrections are fitted after the behavioral
   coefficients. The built-in estimator uses one route search per mode/means, without cost markup.
6. Click **Estimate**. Confirm replacement if the parameter table or named merged OD table already
   exists. The dialog closes, while the main window shows progress and permits cancellation. On
   success, Nodus stores one model's parameters in the database table named after the cost file:
   `uncalibrated.costs` produces `uncalibrated_params`. It adds
   `@paramTable=uncalibrated_params` to that cost file and writes the separate report
   `uncalibrated_params.txt` in the project directory. The cost expressions remain in the file.
7. Later, open **Project → Assignment** and select the same cost file. Its linked parameter table
   selects and locks the matching modal-choice method automatically. Choose the OD matrix to assign:
   the optional merged table, another total-demand table, or a forecast. Set the assignment options
   and click **Assign**. Assignment does not re-estimate parameters.

One parameter table belongs to one selected cost file and holds one embedded modal-choice method.
To keep separate calibrated models, make separate copies of a cost file before
estimating. A project cost file without `@paramTable` can still use coefficient keys stored
directly in that file. 


**Scope and migration:** this implementation supports static, unimodal alternatives. An intermodal
route is reported as an error. Available route
costs must be finite and positive. Positive observed flow without an admissible route causes the
entire group/origin/destination/class observation to be skipped from estimation, across all modes.
Each skipped record and its missing modes, observed quantities and excluded total are described in
the separate estimation report. The report also summarizes how many records were skipped and
retained. Missing routes alone do not trigger an error dialog.

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
with no observed demand shows `n/a` percentages. The report uses all observed calibration demand,
not a later forecast matrix or Assignment's filters. Successful estimation saves these diagnostics
in `<cost-file-stem>_params.txt`. If no usable observations remain, Nodus writes a diagnostic report
without saving parameters.

Exclusions affect only parameter estimation. For example, an observation with 80 road tonnes and
20 waterway tonnes, but only a road route, is excluded from the fit. A later assignment of a selected
OD matrix containing 100 tonnes for that record can allocate all 100 tonnes to road. To assign only
a chosen subset, explicitly select an appropriate OD matrix or assignment filter. The optional
merged OD table sums the source modal demand; it is not filtered by calibration exclusions.

Records with no observed flow in an unavailable mode remain eligible. If all observations are
skipped, Nodus writes a diagnostic report and stops without changing parameters or previous
assignment results. The remaining data must still be sufficient to estimate the chosen model;
other estimation errors are reported normally. Check the skipped records before interpreting a
fit on this reduced population.

If a commodity group has no MNL parameters, assignment displays one warning and uses the former
built-in MNL utility `V = -cost`: a cost factor of **1** and modal constants of **0**. Its route
shares also retain the former exponential rule, proportional to `exp(-route cost)` within each
mode. No defaults are written to the parameter table. Groups with saved coefficients use the fitted
log-cost model; incomplete or invalid coefficients still produce an error. Custom modal split
plugins remain available.

All commodity groups must fit successfully before the parameter table is replaced. The selected
cost file retains its transport-cost expressions and receives the table pointer. Nodus checks
for edits to the selected cost file and the separate report made during estimation before replacing
them. Cancellation or failure does not publish assignment results. After a successful save, the
linked parameters are available to later assignments; the report records convergence,
log-likelihoods and conventional standard errors.

## Estimate a multinomial probit

Choose **Multinomial probit** in the estimation dialog. The linked table selects it automatically
in Assignment. It uses the same cost variable, observed matrices, availability rules and
reference-mode convention as logit. The former built-in Abraham method and its exponent estimator
have been removed.

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

Probit coefficients use their own parameter-table keys (or the same keys in an unlinked legacy
cost file):

```text
probit.(intercept).<mode>.<group> = <intercept>
probit.log(cost).<mode>.<group> = <shared coefficient>
probit.reference.<group> = <reference mode ID>
```

Logit and probit coefficients cannot be interchanged: their error distributions and scales differ.
Re-estimating the selected cost file with another method replaces its linked table after
confirmation. Separate cost files and tables remain independent. All groups must fit before any
new coefficients are saved. Both fitted models use inverse route cost to split each mode's assigned
flow among its routes; this route allocation is not estimated from modal OD data.

If a group has no probit parameters, assignment warns once and uses `V = -cost`, with cost factor
**1**, modal constants **0**, and independent normal errors of variance **1**. As with default MNL,
route allocation is exponential. MNL or proportional keys in an unlinked legacy cost file do not
substitute for missing probit keys; the default probit model remains available.

## Estimate proportional cost adjustment factors

Choose **Proportional** in the estimation dialog; the linked table selects it later in Assignment.
It estimates a positive factor `k(mode, group)` for each mode and commodity group, fixing the
reference factor at **1**:

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

These keys are stored in the selected cost file's parameter table. Re-estimation replaces the
table's prior rows after confirmation. Groups with no proportional entries retain the original
inverse-cost method, with all factors equal to one. A group with proportional entries must provide
every available mode's factor and a reference factor of one;
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
**Project → Modal choice estimation…** to compute route costs, fill the parameter table, and link it
from the selected cost file.

### Data and statistical conventions

Each input row is one OD pair. Estimation maximizes
`sum(OD, mode) quantity(OD, mode) * log(P(OD, mode))`. This is equivalent to weighting the
share-based likelihood by the row's total quantity. It uses quantities
directly, without expanding flows into individual observations.


The solver uses analytical derivatives and damped Newton steps, with scaled log-cost differences,
stable logit exponentials and probit normal integrals. Unidentified models (for example, constant cost ratios when fitting the cost coefficient, disconnected choice
sets or a mode with no observed flow), singular information and nonconvergence produce descriptive
exceptions. It does not add ridge penalties or impose a negative cost coefficient. Inspect the sign
and fit before using an estimate. Perfect or near separation can prevent finite, reliable estimates;
the convergence checks are not a general separation test.

## Optional external estimation examples

The following files demonstrate external R and Biogeme workflow and the separate `MLogit`
plug-in. They do not participate in **Project → Modal choice estimation…**. Keep Nodus open with
the demo project loaded if running them: the example scripts connect to its local HSQLDB server
at `localhost:9001` as `SA` with an empty password. Adjust the connection settings if the project
uses another database or port.

1. Run an uncalibrated multimodal assignment to create a path-header table. Then run
   [`CreateMLogitInput.groovy`](CreateMLogitInput.groovy) in Nodus's Groovy console. Its configured
   source is `demo_path5_header`; adjust that name to the scenario actually assigned. The script
   combines route costs with `od_road`, `od_iww`, and `od_rail` in `mlogit_input`.
2. Run [`MLogit.R`](MLogit.R) with R, RJDBC and `mlogit`, or [`MLogit.py`](MLogit.py) with the
   packages in [`Python-requirements.txt`](Python-requirements.txt) to estimate externally. The R
   script writes `mlogit.txt` and `mlogit_coefs.txt`; its coefficient lines can be copied into a
   **separate cost file without `@paramTable`**.
3. [`MLogit.java`](MLogit.java) implements the external plug-in, built with [`compile.sh`](compile.sh)
   or [`compile.bat`](compile.bat). Select that plug-in and the unlinked cost file in Assignment.
   A file linked to a built-in parameter table locks Assignment to the method stored in that table.
