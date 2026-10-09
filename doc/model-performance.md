# Model performance indicators in Nodus

This note explains every indicator available in **Results > Performance**, its
formula, its reporting scope, and how to interpret its value. The indicators
compare a saved assignment with reference modal origin–destination (OD) matrices.
They assess quantities and modal distributions; they do not directly validate
link volumes, travel times, transport costs, or the feasibility of individual paths.

## 1. Computing a comparison

1. Open the Results dialog and click **Performance**.
2. Associate each selected network **mode ID** with its reference modal OD matrix,
   using the same matrix editor as modal-choice estimation. The IDs must match
   `ldmode` in the assignment results. At least one mapped mode is required.
3. Select the assignment's **path-header table** from the dropdown.
4. Check the indicators to display and click **Compute**.

The computation runs in the background with a busy cursor. **Cancel** stops it;
closing during computation requests cancellation and waits for reading to finish.
The HTML results can be scrolled and the dialog resized. The last matrix mappings,
path-header table, and checkbox choices are remembered independently of estimation
preferences. The comparison does not modify matrices or assignment result tables.

### Inputs and aggregation

Reference matrices require `grp`, `org`, `dst`, and `qty`. Available path-header
tables are identified in the current database schema by their `_header` suffix and
`grp`, `org`, `dst`, `ldmode`, and `qty` fields. A table can contain many paths for
one OD; their quantities are summed before comparison.

Both sides are aggregated by **commodity group, origin, destination, and mode**.
Duplicate matrix rows, paths, OD classes, and departure times are combined. This
matches the aggregation used by `../demo/ComputeWAPE.groovy`. Consequently, this
report does not measure accuracy separately by OD class or departure time.

An absent modal cell is treated as zero. Null quantities are also treated as zero;
negative or nonfinite quantities and invalid OD/mode keys are rejected. Only the
selected modes enter the comparison. Other modes in the assignment table are
listed as excluded, with their assigned quantities.

Reference and assigned data must use the same quantity unit, zone identifiers,
commodity-group definitions, time period, and direction of OD flows. Unexpected
assigned-only ODs or very low coverage can indicate incompatible identifiers or a
wrong scenario/table, rather than a problem with modal behaviour.

## 2. Notation, scope, and missing values

Let $r=(g,o,d)$ identify a commodity group and OD pair, and let $m$ identify a
selected mode. Write:

- $q_{rm}\geq 0$: reference, or observed, quantity;
- $\hat q_{rm}\geq 0$: quantity from the saved assignment;
- $e_{rm}=\hat q_{rm}-q_{rm}$: signed quantity error;
- $\mathcal M$: the selected modes;
- $\mathcal R$: the union of OD/group keys found on either side for those modes.

Totals for one OD/group are

$$
Q_r=\sum_{m\in\mathcal M}q_{rm},
\qquad
\hat Q_r=\sum_{m\in\mathcal M}\hat q_{rm}.
$$

The comparison grid contains every selected mode for every key in
$\mathcal R$, including cells where both quantities are zero. Quantity indicators
use a cell set $\mathcal C$: the complete grid, one mode, one commodity group,
or a particular group/mode intersection. Let $n_{\mathcal C}=|\mathcal C|$.

Distribution indicators use a set $\mathcal S$ of OD/group keys: all commodity
groups combined or one commodity group. Their **eligible ODs** are

$$
\mathcal E_{\mathcal S}
=\{r\in\mathcal S:Q_r>0\ \text{and}\ \hat Q_r>0\}.
$$

For each eligible OD, normalize each side separately:

$$
p_{rm}=\frac{q_{rm}}{Q_r},
\qquad
\hat p_{rm}=\frac{\hat q_{rm}}{\hat Q_r}.
$$

Eligibility requires positive **OD totals**, not positive flow on every mode.
An unavailable waterway mode may therefore have $p_{rm}=\hat p_{rm}=0$ on an
eligible OD. Normalization removes differences in total OD quantity, so share
scores must be read together with quantity and coverage indicators.

**Undefined** means the calculation has no valid denominator or eligible sample.
It is neither a zero error nor an infinite error. For example, a distribution
score is undefined when there are no eligible ODs. A displayed **infinity** is
an actual infinite cross-entropy penalty, as explained below.

## 3. WAPE: weighted absolute percentage error

For the chosen cell set,

$$
\operatorname{WAPE}_{\mathcal C}
=100\,
\frac{\sum_{(r,m)\in\mathcal C}|\hat q_{rm}-q_{rm}|}
     {\sum_{(r,m)\in\mathcal C}q_{rm}}.
$$

**Interpretation:** 0% means every compared quantity is reproduced exactly.
A value of 20% means the total absolute error equals 20% of the reference quantity
in that scope. Lower is better when comparing the same reference data and scope.
Large flows generally have more influence because errors are measured in quantity
units before being normalized.

WAPE is not the unweighted average of cell percentage errors. Unlike MAPE, it does
not divide by each individual observed cell. Zero observed cells are allowed:
assigned flow in such a cell increases the numerator. WAPE is undefined only when
the **total** observed quantity in the reporting scope is zero.

Its range is **0% to no fixed upper limit**. Values above 100% are possible and do
not indicate a calculation error. There is no universal threshold separating a
"good" WAPE from a "bad" one. Expected accuracy depends on the data, aggregation,
market, and intended use.

If every OD retains the same total quantity across the selected modes, the
combined WAPE cannot exceed 200%. Moving one tonne between modes creates two
one-tonne cell errors: one overprediction and one underprediction. In this special
case, WAPE is twice the observed-quantity-weighted mean OD share error in Section 6.
Thus WAPE = 30% corresponds to a 15% minimum redistribution of the total freight
between modes, provided each OD total is conserved.

The report gives WAPE overall, by mode, by commodity group, and by group/mode.

## 4. Relative bias

$$
\operatorname{Bias}_{\mathcal C}
=100\,
\frac{\sum_{(r,m)\in\mathcal C}(\hat q_{rm}-q_{rm})}
     {\sum_{(r,m)\in\mathcal C}q_{rm}}.
$$

**Interpretation:** +10% means assigned quantity is 10% higher than reference
quantity in that scope; -10% means it is 10% lower. The ideal value is zero,
and a smaller absolute bias indicates a better match of the totals.

The range is **-100% to no fixed upper limit**, since assigned quantities are
nonnegative. Bias is undefined if total observed quantity is zero.

Unlike WAPE, signed errors can cancel. A zero bias does not mean accurate cells
or modal shares. When an assignment conserves demand, overall bias can be zero
while one mode is systematically overestimated and another underestimated.
Inspect the mode and group/mode rows alongside WAPE.

## 5. RMSE: root mean squared error

$$
\operatorname{RMSE}_{\mathcal C}
=\sqrt{\frac{1}{n_{\mathcal C}}
       \sum_{(r,m)\in\mathcal C}(\hat q_{rm}-q_{rm})^2}.
$$

**Interpretation:** RMSE is in the same unit as the quantities, for example tonnes.
Zero means a perfect cell-by-cell match. Lower is better for a fixed comparison
scope. Squaring gives large cell errors more influence than an absolute-error
measure, making RMSE useful for detecting poor reproduction of major flows.

Its range is **0 to no fixed upper limit**. It is undefined when there are no
comparison cells. It remains defined when all observed quantities are zero, as
long as the comparison grid is nonempty.

The denominator is the number of cells in the full selected-mode grid, including
cells with zero quantities on both sides. Adding an unused mode or changing the
OD grid can therefore reduce RMSE even if the existing errors remain unchanged.
Compare RMSE only with the same mode selection, evaluation cells, quantity unit,
and aggregation. Values from groups with very different flow magnitudes are not
directly comparable as relative accuracy measures.

## 6. Aggregate modal shares and OD share error

### Aggregate modal shares

For an OD/group scope $\mathcal S$, aggregate reference and assigned shares are

$$
s_m=\frac{\sum_{r\in\mathcal S}q_{rm}}
          {\sum_{r\in\mathcal S}Q_r},
\qquad
\hat s_m=\frac{\sum_{r\in\mathcal S}\hat q_{rm}}
               {\sum_{r\in\mathcal S}\hat Q_r}.
$$

The report displays $100s_m$, $100\hat s_m$, and

$$
\Delta_m=100(\hat s_m-s_m)
\quad\text{in percentage points}.
$$

**Interpretation:** an observed road share of 40% and assigned share of 45% give
an error of **+5 percentage points**, not +5%. A negative difference indicates a
mode's aggregate share is underestimated. Zero differences are the ideal values.
Each share lies between 0% and 100%; a difference lies between -100 and +100
percentage points. A share is undefined if its own side's total quantity is zero;
the difference is undefined if either share is undefined.

These results are shown overall and by commodity group. They describe the global
modal split, not the geographical distribution of flows. Overprediction on one OD
can cancel underprediction on another, so correct aggregate shares do not imply
correct OD shares. Shares can also match while total quantities differ substantially.

### Share error for an individual OD/group

For each eligible OD/group,

$$
T_r=50\sum_{m\in\mathcal M}|\hat p_{rm}-p_{rm}|.
$$

This is the total-variation distance expressed as a percentage. Its range is
**0% to 100%**. Zero means identical modal shares; 100% means the two distributions
put all their freight on different modes.

**Interpretation:** with a common total quantity, $T_r$ is the minimum percentage
of that OD's freight that would need to change modes to reproduce the reference
distribution. If the totals differ, it describes the separately normalized
compositions, not the actual tonnage discrepancy.

Nodus reports the **median** and **90th percentile (P90)** of these errors, overall
and by commodity group. Every eligible OD/group receives equal weight, regardless
of its quantity. The median describes a typical OD; P90 describes the upper part
of the error distribution and helps identify less well reproduced ODs. Lower
values are better. P90 is not the maximum error.

For sorted errors $T_{(1)},\ldots,T_{(n)}$, Nodus uses linear interpolation.
For percentile fraction $u$, set $t=(n-1)u$, $j=\lfloor t\rfloor$, and
$a=t-j$. Then

$$
P_u=(1-a)T_{(j+1)}+aT_{(\lceil t\rceil+1)}.
$$

The median uses $u=0.5$ and P90 uses $u=0.9$. For the three errors 0%, 10%, and
20%, the median is 10% and P90 is 18%. For very small samples, an interpolated P90
need not be the value below which exactly 90% of the observed errors lie.

These statistics are undefined if no OD is eligible. Missing assigned ODs are
not assigned an arbitrary modal distribution and are excluded; their counts and
quantities are reported separately.

## 7. Cross-entropy

For each eligible OD/group,

$$
C_r=-\sum_{m\in\mathcal M}p_{rm}\ln(\hat p_{rm}).
$$

The reporting scope's score is the observed-quantity-weighted mean:

$$
\overline C_{\mathcal S}
=\frac{\sum_{r\in\mathcal E_{\mathcal S}}Q_r C_r}
       {\sum_{r\in\mathcal E_{\mathcal S}}Q_r}.
$$

Natural logarithms are used, so the score is measured in **nats**. Lower is better
when comparing models on the same reference observations and eligible scope.
The range is **0 to infinity**. It is undefined when there are no eligible ODs.
An assigned distribution that gives a very small share to an observed mode
receives a large penalty.

Cross-entropy is **not generally zero for a perfect match**. Its minimum for the
fixed observed shares is their weighted entropy, where

$$
H_r=-\sum_{m\in\mathcal M}p_{rm}\ln(p_{rm}).
$$

For instance, when the reference split is 50% road and 50% rail, predicting those
shares exactly gives $C_r=\ln(2)\approx0.6931$. A zero score is possible when the
reference and prediction both put all flow on the same mode. Different commodity
groups can have different inherent modal entropies; their raw cross-entropies
should not be interpreted as directly comparable fit errors without that context.

### Zero flows and infinite cross-entropy

The standard zero-share convention is:

| Observed share | Assigned share | Cross-entropy contribution |
| --- | --- | --- |
| 0 | 0 or positive | 0 |
| Positive | Positive | $-p_{rm}\ln(\hat p_{rm})$ |
| Positive | 0 | Infinity |

One observed-positive modal cell with zero assigned flow on an eligible OD makes
the entire reporting scope's cross-entropy infinite. This does not mean every OD
is poorly reproduced. It can reflect an unavailable alternative, assignment
rules, or a small positive flow rounded to zero in the saved results. The score
uses **saved quantities**, not the model's unrounded internal choice probabilities.
No probability floor or smoothing is applied.

When infinite cross-entropy is selected, the report gives the affected eligible
OD count, affected modal-cell count, observed quantity in those cells, and up to
20 example cells with the largest observed quantities. These diagnostics concern
eligible ODs only; completely unassigned ODs are reported separately.

## 8. Jensen–Shannon divergence

For each eligible OD/group, form the mean distribution

$$
\mu_{rm}=\frac{p_{rm}+\hat p_{rm}}{2}.
$$

The Jensen–Shannon divergence is

$$
J_r=\frac12\sum_{m\in\mathcal M}p_{rm}
             \log_2\!\left(\frac{p_{rm}}{\mu_{rm}}\right)
   +\frac12\sum_{m\in\mathcal M}\hat p_{rm}
             \log_2\!\left(\frac{\hat p_{rm}}{\mu_{rm}}\right).
$$

Its overall or commodity-group value is

$$
\overline J_{\mathcal S}
=\frac{\sum_{r\in\mathcal E_{\mathcal S}}Q_r J_r}
       {\sum_{r\in\mathcal E_{\mathcal S}}Q_r}.
$$

**Interpretation:** with the base-2 logarithms used by Nodus, the range is
**0 to 1**. Zero means matching distributions; 1 means disjoint distributions,
with no mode carrying positive shares on both sides. Lower values are better.
Intermediate values measure composition disagreement, but are not percentages of
misallocated tonnes. There is no universal acceptable-value threshold.

Zero-share terms contribute zero. If one side has a positive share, its mean share
$\mu_{rm}$ is also positive. As a result, zero observed or assigned shares do not
produce infinite Jensen–Shannon divergence. A waterway mode with zero flow on both
sides contributes nothing. For $(0.3,0.7,0)$ versus $(0,1,0)$, the divergence is
approximately 0.1692; for $(1,0,0)$ versus $(0,1,0)$, it is 1.

Nodus reports the **divergence**, not the square-root Jensen–Shannon distance.
The divergence for a single OD is symmetric in the observed and assigned share
vectors. The report then uses reference quantities as weights; swapping the full
observed and assigned quantity datasets can change those weights when OD totals
differ. The reference data and weights should be held fixed when comparing models.

This is useful for comparing sparse assignments whose cross-entropy is infinite.
It still ignores total-quantity differences through share normalization and is
undefined with no eligible ODs. It replaces the former KL checkbox; saved KL
selections automatically select Jensen–Shannon when reopened.

## 9. Coverage and zero-prediction diagnostics

These diagnostics explain which observations the assignment reproduces or misses.
They are computed overall and by commodity group, with modal zero-prediction
counts also available by mode and group/mode.

### Observed and eligible OD counts

Using an indicator function $\mathbf1[\cdot]$, the observed OD count is

$$
N_{\mathrm{obs}}=\sum_{r\in\mathcal S}\mathbf1[Q_r>0].
$$

The eligible count displayed with distribution scores is

$$
N_{\mathrm{eligible}}
=\sum_{r\in\mathcal S}\mathbf1[Q_r>0\ \text{and}\ \hat Q_r>0].
$$

An OD appearing in several commodity groups counts separately for each group.
These counts describe the evaluated sample, rather than its accuracy by themselves.

### Quantity coverage on ODs with some assigned flow

$$
\operatorname{Coverage}_{\mathcal S}
=100\,
\frac{\sum_{r\in\mathcal S}Q_r\mathbf1[\hat Q_r>0]}
     {\sum_{r\in\mathcal S}Q_r}.
$$

The range is **0% to 100%**; higher is better for inclusion of observed demand.
It is undefined with no observed quantity. A value of 100% means every observed
OD carrying reference quantity has **some** assigned flow across the selected
modes. It does **not** mean all its quantity was assigned correctly. For example,
one assigned tonne on an OD with 1,000 observed tonnes is enough for that OD to
count as covered. Inspect quantity shortfalls and modal-cell errors as well.

### Observed ODs without assigned flow

$$
N_{\mathrm{missing}}
=\sum_{r\in\mathcal S}\mathbf1[Q_r>0\ \text{and}\ \hat Q_r=0],
\qquad
Q_{\mathrm{missing}}
=\sum_{r\in\mathcal S}Q_r\mathbf1[\hat Q_r=0].
$$

The count and observed quantity have ideal values of zero. These ODs are excluded
from distribution scores because no assigned distribution can be normalized.

### OD quantity shortfall and excess

$$
S_{\mathcal S}=\sum_{r\in\mathcal S}\max(Q_r-\hat Q_r,0),
\qquad
X_{\mathcal S}=\sum_{r\in\mathcal S}\max(\hat Q_r-Q_r,0).
$$

Both are in quantity units, nonnegative, and ideally zero. Shortfall measures
underassignment relative to reference OD totals; excess measures overassignment.
They are computed separately so that opposite OD discrepancies do not cancel.
Equal global totals can coexist with substantial shortfalls and excesses.
These diagnostics assess OD totals, so incorrect modal splits can remain even
when both values are zero.

### Assigned-only ODs

$$
N_{\mathrm{extra}}
=\sum_{r\in\mathcal S}\mathbf1[Q_r=0\ \text{and}\ \hat Q_r>0].
$$

The ideal count is zero when both datasets are intended to cover the same demand.
Their assigned quantities contribute to excess. They are excluded from share
scores because no positive observed total exists.

### Observed-positive, assigned-zero modal cells

Let

$$
\mathcal Z_{\mathcal C}
=\{(r,m)\in\mathcal C:q_{rm}>0\ \text{and}\ \hat q_{rm}=0\}.
$$

The report gives

$$
N_{\mathrm{zero}}=|\mathcal Z_{\mathcal C}|,
\qquad
Q_{\mathrm{zero}}=\sum_{(r,m)\in\mathcal Z_{\mathcal C}}q_{rm}.
$$

These identify observed modal flows absent from the assigned matrix. Unlike the
infinite-cross-entropy diagnostics, they also include completely unassigned ODs.
Zero observed waterway flow on an infeasible route does not enter this set.
A zero observation alone, however, does not establish infeasibility: a mode can
be available but unused.

### Reference coverage versus assignment demand coverage

All diagnostics above compare with the **chosen reference matrices**. They cannot
establish the assignment's original unassigned-demand rate if its input demand
has different OD totals, groups, or coverage. A difference can be legitimate when
comparing different demand scenarios. To interpret it as assignment failure, first
verify that reference quantities and assignment input demand are intended to match.

## 10. Worked example

Consider one commodity group, two ODs and two selected modes, in tonnes:

| OD | Mode | Observed | Assigned | Signed error |
| --- | --- | ---: | ---: | ---: |
| A | Road | 60 | 50 | -10 |
| A | Waterway | 40 | 50 | +10 |
| B | Road | 20 | 40 | +20 |
| B | Waterway | 80 | 60 | -20 |

Each OD has 100 tonnes on both sides. The resulting indicators are:

| Indicator | Value | Interpretation |
| --- | ---: | --- |
| Overall WAPE | 30% | 60 tonnes of absolute modal-cell error relative to 200 observed tonnes. |
| Overall bias | 0% | Totals match, although the modal cells do not. |
| Road bias / waterway bias | +12.5% / -8.3333% | Road quantity is overestimated; waterway quantity underestimated. |
| RMSE | 15.8114 tonnes | Square root of the mean of 100, 100, 400 and 400. |
| Road share difference | +5 percentage points | Observed road share 40%; assigned road share 45%. |
| Waterway share difference | -5 percentage points | Observed waterway share 60%; assigned waterway share 55%. |
| OD share errors | 10% and 20% | Composition errors for A and B. |
| Median / P90 OD share error | 15% / 19% | Equal OD weights and linear percentile interpolation. |
| Cross-entropy | 0.6425 nats | Quantity-weighted mean of the two OD cross-entropies. |
| Jensen–Shannon divergence | 0.0211 | Quantity-weighted mean of base-2 OD divergences, not their square roots. |
| Coverage | 100% | Both observed ODs have some assigned quantity. |
| Missing ODs / assigned-only ODs | 0 / 0 | Both sides contain the same positive-demand ODs. |
| Quantity shortfall / excess | 0 / 0 tonnes | Every OD total matches. |
| Observed-positive, assigned-zero cells | 0 | No observed modal cell is entirely missed. |

This example shows why a zero overall bias, full coverage and matching OD totals
do not establish a correct modal split. It also shows the factor of two between
WAPE and redistributed freight: the minimum modal redistribution is 15% of the
200-tonne total, while WAPE is 30%.

## 11. Choosing and comparing indicators

| Question | Useful indicators |
| --- | --- |
| How much total cell error is there relative to freight volume? | WAPE |
| Is a mode systematically overestimated or underestimated? | Relative bias by mode and group/mode |
| Are some large flows badly reproduced? | RMSE, alongside WAPE |
| Is the overall modal split correct? | Aggregate modal-share differences |
| How well are typical and difficult ODs reproduced? | Median and P90 OD share error |
| How do modal distributions compare when many shares are zero? | Jensen–Shannon divergence |
| Does the assignment give implausibly small or zero shares to observed flows? | Cross-entropy and its zero-share diagnostics |
| Are whole ODs missing, or are total OD quantities mismatched? | Coverage, missing/extra ODs, shortfall and excess |

For routine assessment, use WAPE, modal-share differences, median/P90 share error,
Jensen–Shannon divergence and coverage together. Add relative bias to diagnose
direction and RMSE to examine sensitivity to large errors. Cross-entropy is useful
as a distribution diagnostic, but infinite values cannot rank affected models.
None of these indicators has a universal good/bad threshold independent of the
application and comparison scope.

Always use the same reference observations, selected modes, units and aggregation
for competing models. If their eligible OD sets differ, compare coverage and
consider a common eligible subset before ranking share scores. A good score on a
small, easy subset is not evidence of accurate prediction of all freight flows.

For **predictive validation**, evaluate ODs or periods not used to estimate the
parameters. OD pivots must not be fitted to the validation observations. Comparing
an assignment with the same matrices used for calibration measures reproduction
of the calibration data, which can be much better than performance on new demand.

Tonnage weighting describes freight-volume performance; tonnes are not independent
choice observations. The displayed values are descriptive scores, not significance
tests, confidence intervals or a substitute for a suitable sampling design.

## 12. References

- [Forecasting: Principles and Practice — evaluating point forecast accuracy](https://otexts.com/fpp3/accuracy.html): absolute and squared errors, percentage errors, and evaluation on unused data.
- [SciPy entropy documentation](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.entropy.html): entropy, cross-entropy and logarithm conventions.
- [SciPy relative-entropy convention](https://docs.scipy.org/doc/scipy/reference/generated/scipy.special.rel_entr.html): zero observed shares and zero predicted shares.
- [SciPy Jensen–Shannon documentation](https://docs.scipy.org/doc/scipy/reference/generated/scipy.spatial.distance.jensenshannon.html): the mean-distribution definition and distinction between divergence and its square-root distance.
