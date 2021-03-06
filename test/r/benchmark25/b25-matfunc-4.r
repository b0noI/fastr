# extracted from R Benchmark 2.5 (06/2008) [Simon Urbanek]
# http://r.research.att.com/benchmarks/R-benchmark-25.R

# II. Matrix functions
# Cholesky decomposition of a 3000x3000 matrix

b25matfunc <- function(args) {
  runs = if (length(args)) as.integer(args[[1]]) else 6L

  for (i in 1:runs) {
    a <- rnorm(3000*3000)
    dim(a) <- c(3000, 3000)
    a <- crossprod(a, a)
    b <- chol(a)
  }
}

if (!exists("i_am_wrapper")) {
  b25matfunc(commandArgs(trailingOnly=TRUE))
}
