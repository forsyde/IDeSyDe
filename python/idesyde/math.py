import numpy as np
import sympy


def integralize_vector(vec: sympy.Matrix):
    factor = vec[0]
    for elem in vec:
        factor = elem.gcd(factor)
    scaled = [[v / factor] for v in vec]
    return sympy.Matrix(scaled)
