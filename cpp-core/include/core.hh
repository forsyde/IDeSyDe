#if !defined(CORE_H)
#define CORE_H

namespace core
{

#include <headers.hh>

    class DecisionModel
    {
        virtual DecisionModelHeader header();
    };

    class DesignModel
    {
        virtual DesignModelHeader header();
    };
}
#endif // CORE_H
