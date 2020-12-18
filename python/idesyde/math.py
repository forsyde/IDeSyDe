import numpy as np
import sympy


def integralize_vector(vec: sympy.Matrix):
    '''Scale vector to have all elements as integers

    Arguments:
        vec: It is expected to be a sympy Matrix, but maybe
        other bi-iterable objects can work as well, as long
        as sympy can compute the GCD of the input values.

    Returns:
        Integralized vector. Note that this works solely on the
        input vector and therefore no mathematical properties
        are guaranteed except that a common factor is applied
        to all the vector's entries.
    '''
    factor = vec[0]
    for elem in vec:
        factor = elem.gcd(factor)
    scaled = [[v / factor] for v in vec]
    return sympy.Matrix(scaled)
