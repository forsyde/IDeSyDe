#if !defined(H_COMMON)
#define H_COMMON

#include <core.hh>

namespace idesyde::common
{

    class SDFApplication : idesyde::core::DecisionModel
    {
    private:
        /* data */
    public:
        SDFApplication(/* args */);
        ~SDFApplication();
    };

    SDFApplication::SDFApplication(/* args */)
    {
    }

    SDFApplication::~SDFApplication()
    {
    }

    class SDFToTiledMulticore : idesyde::core::DecisionModel
    {
    private:
        /* data */
    public:
        SDFToTiledMulticore(/* args */);
        ~SDFToTiledMulticore();
    };

    SDFToTiledMulticore::SDFToTiledMulticore(/* args */)
    {
    }

    SDFToTiledMulticore::~SDFToTiledMulticore()
    {
    }

} // namespace common

#endif // H_COMMON
